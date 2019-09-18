package controllers

import akka.actor.ActorSystem
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.sharding.typed.scaladsl.EntityRef
import akka.cluster.sharding.typed.scaladsl.{Entity => ShardedEntity}
import akka.cluster.Cluster
import akka.cluster.sharding.typed.scaladsl.EntityContext
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.EventSourcedBehavior
import akka.util.Timeout
import javax.inject._
import libs.PersistenceIds
import libs.ProjectionTaggers
import persistence.Confirmed
import persistence.GetGreetings
import persistence.GreetingsChanged
import persistence.GreetingsCommand
import persistence.GreetingsPersistentEntity
import persistence.GreetingsState
import persistence.Rejected
import persistence.UpdateGreetings
import play.api.libs.json.JsValue
import play.api.mvc._

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.reflect.ClassTag

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
      (pp.shardedRefFor(id) ? GetGreetings).map {
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
          (pp.shardedRefFor(id) ? UpdateGreetings(msg)).map {
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

abstract class PlayPersistenceProvisions[Cmd: ClassTag, Evt, St](
    actorSystem: ActorSystem
)(implicit executionContext: ExecutionContext) {

  // - - - - - Machinery - - - - -
  // Should be handled by Play
  import akka.actor.typed.scaladsl.adapter._
  private val typedSystem: akka.actor.typed.ActorSystem[_] = actorSystem.toTyped
  protected val sharding = ClusterSharding(typedSystem)
  private val cluster: Cluster = Cluster(actorSystem)
  cluster.join(cluster.selfAddress)

  // - - - - - Abstract details - - - - -
  // Abstract details: sharding
  protected def shardingName: String
  protected def adaptFromSharding: EntityContext => PersistenceId

  // Abstract details: persistence
  protected def journalName: String
  protected def createPersistentBehavior
    : PersistenceId => EventSourcedBehavior[Cmd, Evt, St]

  // Abstract details: tagging
  protected def taggerPrefix: String
  protected def byEntityTagger: PersistenceId => String

  // - - - - - Public API - - - - -
  final def shardedRefFor(persistentEntityId: String): EntityRef[Cmd] =
    sharding.entityRefFor(shardingTypeKey, persistentEntityId)

  // - - - - - Private details - - - - -
  private val shardingTypeKey: EntityTypeKey[Cmd] =
    EntityTypeKey[Cmd](shardingName)

  // This helps hide the fact that the Behavior is sharded
  private val createBehavior
    : EntityContext => EventSourcedBehavior[Cmd, Evt, St] = entityCtx => {
    val id = adaptFromSharding(entityCtx)
    createPersistentBehavior(id)
  }

  sharding.init(ShardedEntity(shardingTypeKey, createBehavior))

}
@Singleton
class PersistenceProvisions @Inject()(actorSystem: ActorSystem)(
    implicit executionContext: ExecutionContext
) extends PlayPersistenceProvisions[
      GreetingsCommand,
      GreetingsChanged,
      GreetingsState
    ](actorSystem) {

  // sharding concerns
  protected lazy val shardingName = "greetings"
  protected lazy val adaptFromSharding: EntityContext => PersistenceId =
    ctx => PersistenceIds.asLagomScala(journalName, ctx.entityId)

  // persistence concerns
  protected lazy val journalName = "greetings"
  protected val createPersistentBehavior
    : PersistenceId => EventSourcedBehavior[GreetingsCommand,
                                            GreetingsChanged,
                                            GreetingsState] = {
    peid: PersistenceId =>
      GreetingsPersistentEntity
        .behavior(peid)
        .withTagger(event => Set.empty[String] + byEntityTagger(peid))
  }

  // tagging concerns
  protected lazy val taggerPrefix = "greetings"
  protected lazy val byEntityTagger: PersistenceId => String =
    ProjectionTaggers.lagomProjectionsTagger(taggerPrefix, 8)

}
