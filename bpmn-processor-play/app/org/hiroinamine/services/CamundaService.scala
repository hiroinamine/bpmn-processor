package org.hiroinamine.services

import java.io.InputStream

import akka.actor.ActorSystem
import com.google.inject.Injector
import javax.inject.Inject
import org.camunda.bpm.engine.impl.cfg.{ProcessEnginePlugin, StandaloneProcessEngineConfiguration}
import org.camunda.bpm.engine.impl.jobexecutor.DefaultJobExecutor
import org.camunda.bpm.engine.impl.persistence.StrongUuidGenerator
import org.camunda.bpm.engine.runtime.{Execution, ProcessInstanceWithVariables}
import org.camunda.bpm.engine.{ArtifactFactory, ProcessEngineConfiguration}
import org.camunda.connect.plugin.impl.ConnectProcessEnginePlugin
import org.camunda.spin.plugin.impl.SpinProcessEnginePlugin
import org.hiroinamine.domains.{CamundaResponse, MessageNotFoundFailure, ResponseTimeoutFailure}
import play.api.cache.AsyncCacheApi
import play.api.db.DBApi
import play.api.inject.ApplicationLifecycle
import play.api.{Configuration, Logger}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

class CamundaService @Inject()(dBApi: DBApi,
                               config: Configuration,
                               lifecycle: ApplicationLifecycle,
                               injector: Injector,
                               system: ActorSystem,
                               cache: AsyncCacheApi) {
  val logger = Logger(this.getClass)
  val processEngine = init(dBApi)
  val syncWaitTime = config.getOptional[Int]("cache.sync-wait-time").getOrElse(5).seconds
  val cacheDuration = syncWaitTime + 10.seconds
  implicit val ec: ExecutionContext = system.dispatchers.lookup("contexts.camunda-executor")
  // import scala.concurrent.ExecutionContext.Implicits.global

  protected def init(dBApi: DBApi, enableJob: Boolean = false) = {
    val plugins: Seq[ProcessEnginePlugin] = Seq(
      new ConnectProcessEnginePlugin,
      new SpinProcessEnginePlugin
    )

    val strongUuidGenerator = new StrongUuidGenerator()

    val configuration = new StandaloneProcessEngineConfiguration() {

      /** Set data source */
      setDataSource(dBApi.database("default").dataSource)

      /** Check db schema */
      setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE)

      /** Set the History */
      setHistory(ProcessEngineConfiguration.HISTORY_NONE)

      /** Starts the Job executor */
      setJobExecutorActivate(enableJob)

      /** Setup Camunda plugins */
      setProcessEnginePlugins(plugins.asJava)

      /** Setup uuid generator */
      setIdGenerator(strongUuidGenerator)

      /**
        * Assists Camunda in the creation of artifacts with all dependencies resolved.
        */
      setArtifactFactory(new ArtifactFactory {
        override def getArtifact[T](clazz: Class[T]): T =
          injector.getBinding(clazz).getProvider.get()
      })

      override def initJobExecutor(): Unit = {
        super.initJobExecutor()
        jobExecutor.asInstanceOf[DefaultJobExecutor].setCorePoolSize(20)
        jobExecutor.asInstanceOf[DefaultJobExecutor].setMaxPoolSize(50)
        jobExecutor.asInstanceOf[DefaultJobExecutor].setQueueSize(48)
        jobExecutor.setMaxJobsPerAcquisition(48)
        jobExecutor.setLockTimeInMillis(1000 * 60 * 60 * 5)
      }
    }

    logger.info("Starting process engine...")
    val engine = configuration.buildProcessEngine()
    logger.info("Built process engine.")
    engine
  }

  lifecycle.addStopHook { () =>
    Future.successful {
      logger.info("Shutting down camunda engine...")
      processEngine.close()
    }
  }

  def fire(realmId: String,
           name: String,
           sessionId: String,
           variables: Map[String, AnyRef],
           sync: Boolean): CamundaResponse = {
    Future {
      val start = System.currentTimeMillis()
      execute(realmId, name, sessionId, variables)
      val duration = System.currentTimeMillis() - start
      logger.info(s"$duration ms")
    } onComplete {
      case Success(p) =>
//        logger.info(
//          s"processed ${p.getProcessDefinitionId} with ssid #$sessionId, pid #${p.getProcessInstanceId}, ended: #${p.isEnded}")
      case Failure(error) =>
        logger.error(s"error on fire workflow $name with session #$sessionId", error)
    }

    if (sync) syncProcess(sessionId) else CamundaResponse(sessionId)
  }

  def continue(realmId: String,
               name: String,
               sessionId: String,
               messageName: String,
               variables: Map[String, AnyRef],
               sync: Boolean): CamundaResponse = {
    findExecution(realmId, name, sessionId, messageName) match {
      case Some(execution) =>
        Future {
          processEngine.getRuntimeService.signal(execution.getId, toJava(variables))
        } onComplete {
          case Success(_) =>
            logger.info(
              s"continued msg $messageName with ssid #$sessionId, pid #${execution.getProcessInstanceId}, ended: #${execution.isEnded}")
          case Failure(error) =>
            logger.error(
              s"error on continue workflow $name with msg $messageName, ssid #$sessionId, pid #${execution.getProcessInstanceId}",
              error)
        }
        if (sync) syncProcess(sessionId) else CamundaResponse(sessionId)

      case None =>
        throw MessageNotFoundFailure(s"msg #$messageName or ssid #$sessionId not found")
    }
  }

  def deploy(realmId: String, name: String, inputStream: InputStream): Either[String, String] = {
    Try {
      val deployment = processEngine.getRepositoryService.createDeployment().tenantId(realmId)
      deployment
        .addInputStream(name, inputStream)
        .enableDuplicateFiltering(true)
        .deploy()
    } match {
      case Success(d) =>
        Right(d.getId)
      case Failure(error) =>
        logger.error("deployment error", error)
        Left(error.getMessage)
    }
  }

  /**
    * Nested conversion to Java type.
    *
    * @param map The Scala map to be converted
    * @return The Java map
    */
  protected def toJava(map: Map[String, Any]): java.util.Map[String, Object] = {
    val variables = new java.util.HashMap[String, AnyRef]()
    for ((key, value) <- map) {
      value match {
        case map: Map[String, Any] @unchecked =>
          variables.put(key, toJava(map))
        case seq: Seq[_] =>
          val items = seq.map {
            case e: Map[String, Any] @unchecked => toJava(e)
            case e                              => e
          }
          variables.put(key, items.asJavaCollection)
        case v: AnyRef => variables.put(key, v)
        case _         => ()
      }
    }
    variables
  }

  protected def execute(realmId: String,
                        name: String,
                        sessionId: String,
                        variables: Map[String, AnyRef]): ProcessInstanceWithVariables = {
    processEngine.getRuntimeService
      .createProcessInstanceByKey(name)
      .businessKey(sessionId)
      .setVariables(toJava(variables))
      .processDefinitionTenantId(realmId)
      .executeWithVariablesInReturn()
  }

  protected def findExecution(realmId: String,
                              name: String,
                              sessionId: String,
                              messageName: String): Option[Execution] = {
    processEngine.getRuntimeService
      .createExecutionQuery()
      .processDefinitionKey(name)
      .processInstanceBusinessKey(sessionId)
      .tenantIdIn(realmId)
      .messageEventSubscriptionName(messageName)
      .list()
      .asScala
      .headOption
  }

  protected def syncProcess(sessionId: String): CamundaResponse = {
    val promise = Promise[String]()
    cache.set(sessionId, promise, cacheDuration)
    Try(Await.result(promise.future, syncWaitTime)) match {
      case Success(response) =>
        CamundaResponse(sessionId, Some(response))
      case Failure(_) =>
        throw ResponseTimeoutFailure(s"Response not received from sid #$sessionId")
    }
  }
}
