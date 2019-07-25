package org.hiroinamine.controllers

import java.util.UUID

import javax.inject.Inject
import org.hiroinamine.domains.ResponseTimeoutFailure
import org.hiroinamine.services.CamundaService
import org.json4s.native.Serialization._
import play.api.libs.json.Json
import play.api.mvc._

import scala.util.{Failure, Success, Try}

class BpmnController @Inject()(cc: ControllerComponents, camundaService: CamundaService)
    extends AbstractController(cc) {

  implicit val formats = org.json4s.DefaultFormats + org.json4s.ext.DateTimeSerializer

  implicit def jsonToMap(json: String): Map[String, AnyRef] =
    read[Map[String, AnyRef]](json)

  def fire(id: String, sessionId: Option[String], sync: Boolean) = Action(parse.json) { req =>
    val session = sessionId.getOrElse(UUID.randomUUID().toString)
    val realmId = req.headers.get("Realm").getOrElse("realm1")
    val body = req.body.toString(): Map[String, AnyRef]

    Try(camundaService.fire(realmId, id, session, body, sync)) match {
      case Success(response) => Ok(write(response)).as(JSON)
      case Failure(ResponseTimeoutFailure(error)) =>
        GatewayTimeout(Json.obj("msg" -> error.toString))
      case Failure(error) =>
        InternalServerError(Json.obj("msg" -> error.toString))
    }
  }

}
