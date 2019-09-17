package controllers

import akka.actor.ActorSystem
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.sharding.typed.scaladsl.EntityRef
import akka.cluster.sharding.typed.scaladsl.{ Entity => ShardedEntity }
import akka.cluster.Cluster
import akka.util.Timeout
import javax.inject._
import persistence.Confirmed
import persistence.GetGreetings
import persistence.GreetingsCommand
import persistence.GreetingsPersistentEntity
import persistence.Rejected
import persistence.UpdateGreetings
import play.api.libs.json.JsValue
import play.api.mvc._

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * This controller creates an `Action` to handle HTTP requests to the
  * application's home page.
  */
@Singleton
class HomeController @Inject()(
  pp: PersistenceProvisions,
  cc: ControllerComponents
)(implicit ctx: ExecutionContext)
    extends AbstractController(cc) {

  private implicit val askTimeout: Timeout = Timeout(5.seconds)

  /**
    * $ curl http://localhost:9000/api/hello/Alice
    */
  def index(id: String) = Action.async {
    implicit request: Request[AnyContent] =>
      (pp.forId(id) ? GetGreetings).map {
        case Confirmed(message) => Ok(s"$message, $id!")
        case Rejected(cause)    => BadRequest(s"$cause")
      }
  }

  /**
    * $ curl -X POST -H "Content-Type: application/json" -d '{"message": "Hi"}'  http://localhost:9000/api/hello/Alice
    */
  def updateGreeting(id: String) = Action.async {
    implicit request: Request[AnyContent] =>
      val body: AnyContent = request.body
      val jsonBody: Option[JsValue] = body.asJson

      jsonBody
        .map { json =>
          val msg = (json \ "message").as[String]
          (pp.forId(id) ? UpdateGreetings(msg)).map {
            case Confirmed(message) => Ok(s"Udapted to $message!")
            case Rejected(cause)    => BadRequest(s"$cause")
          }
        }
        .getOrElse {
          Future
            .successful(BadRequest("Expecting application/json request body"))
        }
  }
}
@Singleton
class PersistenceProvisions @Inject()(actorSystem: ActorSystem)(
  implicit executionContext: ExecutionContext
) {
  import akka.actor.typed.scaladsl.adapter._

  private val typedSystem: akka.actor.typed.ActorSystem[_] = actorSystem.toTyped
  private val sharding = ClusterSharding(typedSystem)

  private val cluster: Cluster = Cluster(actorSystem)
  cluster.join(cluster.selfAddress)

  private val typeKey =
    EntityTypeKey[GreetingsCommand]("greetings-sharded-entity")
  sharding.init(ShardedEntity(typeKey, GreetingsPersistentEntity.behavior))

  def forId(persistentEntityId: String): EntityRef[GreetingsCommand] = {
    val peId = typeKey.persistenceIdFrom(persistentEntityId)
    sharding.entityRefFor(typeKey, peId.id)
  }

}
