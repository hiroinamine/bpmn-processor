package org.hiroinamine.listeners

import com.fasterxml.jackson.databind.ObjectMapper
import javax.inject.Inject
import org.camunda.bpm.engine.delegate.{DelegateExecution, ExecutionListener}
import play.api.Logger

class DebugListener @Inject()() extends ExecutionListener {
  val logger = Logger(this.getClass)

  override def notify(execution: DelegateExecution): Unit = {
    // log java map
    val mapper = new ObjectMapper()
    logger.debug(s"${execution.getActivityInstanceId}:${mapper.writer().writeValueAsString(execution.getVariables)}")
  }
}
