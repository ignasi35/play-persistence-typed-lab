package controllers

import javax.inject._
import play.api._
import play.api.libs.json.JsValue
import play.api.mvc._

/**
  * This controller creates an `Action` to handle HTTP requests to the
  * application's home page.
  */
@Singleton
class HomeController @Inject()(cc: ControllerComponents)
    extends AbstractController(cc) {

  /**
    * $ curl http://localhost:9000/api/hello/Alice
    */
  def index(id: String) = Action { implicit request: Request[AnyContent] =>
    Ok(s"Hello, $id!")
  }

  /**
    * $ curl -X POST -H "Content-Type: application/json" -d '{"message": "Hi"}'  http://localhost:9000/api/hello/Alice
    */
  def updateGreeting(id: String) = Action { request: Request[AnyContent] =>
    val body: AnyContent = request.body
    val jsonBody: Option[JsValue] = body.asJson
    jsonBody
      .map { json =>
        Ok("Got: " + (json \ "message").as[String])
      }
      .getOrElse {
        BadRequest("Expecting application/json request body")
      }
  }
}
