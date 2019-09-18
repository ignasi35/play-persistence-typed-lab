package controllers

import akka.actor.ActorSystem
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.sharding.typed.scaladsl.EntityRef
import akka.cluster.sharding.typed.scaladsl.{Entity => ShardedEntity}
import akka.cluster.Cluster
import akka.cluster.sharding.typed.scaladsl.EntityContext
import akka.cluster.sharding.typed.scaladsl.EventSourcedEntity
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.EventSourcedBehavior
import akka.util.Timeout
import javax.inject._
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

  // purposedly not using `PersistenceId since that's already used by Akka Persistence
  type PEId = String
  type ShardedInstanceId = String

  // MACHINERY - Should be handled by Play
  import akka.actor.typed.scaladsl.adapter._
  private val typedSystem: akka.actor.typed.ActorSystem[_] = actorSystem.toTyped
  protected val sharding = ClusterSharding(typedSystem)
  private val cluster: Cluster = Cluster(actorSystem)
  cluster.join(cluster.selfAddress)

  protected def journalName: String
  protected def shardingName: String

  protected def adaptToSharding: PEId => ShardedInstanceId
  protected def adaptFromSharding: ShardedInstanceId => PEId
  protected def createPersistentBehavior
    : PEId => EventSourcedBehavior[Cmd, Evt, St]

  // this code helps adapt sharding types to Persistence Types
  def shardedRefFor(persistentEntityId: PEId): EntityRef[Cmd] =
    sharding.entityRefFor(
      shardingTypeKey,
      // prepares a plain persistenceId into a entityId  -- needs some more work
      adaptToSharding(persistentEntityId)
    )

  protected val shardingTypeKey: EntityTypeKey[Cmd] =
    EntityTypeKey[Cmd](shardingName)

  protected type CreateShardedBehavior =
    EntityContext => EventSourcedBehavior[Cmd, Evt, St]
  private val createBehavior: CreateShardedBehavior = entityCtx => {
    val shardedEntityId: ShardedInstanceId = entityCtx.entityId
    val id: PEId = adaptFromSharding(shardedEntityId)
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

  protected lazy val journalName = "greetings"
  protected lazy val shardingName = "greetings-sharded-entity"

  private val pe = new GreetingsPersistentEntity(journalName)

  // The user-provided PEId is used as a sharding Id. This sharding Id is
  lazy val adaptToSharding: PEId => ShardedInstanceId = pe.serializeArguments
  lazy val adaptFromSharding: ShardedInstanceId => PEId =
    pe.deserializeArguments

  private val byEntityTagger: PEId => String =
    lagomProjectionsTagger(journalName, 8)

  protected val createPersistentBehavior
    : PEId => EventSourcedBehavior[GreetingsCommand,
                                   GreetingsChanged,
                                   GreetingsState] = { peid: PEId =>
    pe.behavior(pe.persistenceIdFrom(peid))
      .withTagger(event => Set.empty[String] + byEntityTagger(peid))
  }

  // A Tagger that given a PEId produces a sharded tag. This is necessary to
  // build projections over this journal
  private def lagomProjectionsTagger(journalName: String,
                                     numOfShards: Int): PEId => String = {
    persistenceId =>
      val shardNum = Math.abs(persistenceId.hashCode % numOfShards)
      s"$journalName$shardNum"
  }

}
