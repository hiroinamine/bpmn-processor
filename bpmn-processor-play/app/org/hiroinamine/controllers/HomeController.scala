package org.hiroinamine.controllers

import javax.inject._
import play.api.mvc._

@Singleton
class HomeController @Inject()(cc: ControllerComponents) extends AbstractController(cc) {

  def index() = Action { implicit request: Request[AnyContent] =>
    Ok(org.hiroinamine.BuildInfo.toJson).as(JSON)
  }
}
