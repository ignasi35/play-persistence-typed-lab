package persistence

import akka.actor.typed.ActorRef
import akka.cluster.sharding.typed.scaladsl.EntityContext
import akka.persistence.typed.ExpectingReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.Effect
import akka.persistence.typed.scaladsl.EventSourcedBehavior
import akka.persistence.typed.scaladsl.ReplyEffect

class GreetingsPersistentEntity(val journalName: String) {

  // -- needs some more work
  // Here's some random de/ser logic so journalName and persistenceId travel together as identifiers when sharding
  def serializeArguments(persistenceId: String):String = s"shard-name-$journalName-$persistenceId"
  // the journalName is ignored when deserializing but it could be used. What's important is that this de/ser logic
  // allows passing any info across the entity context.
  def deserializeArguments(entityId: String):String = entityId.split("-").drop(3).mkString("-")
  // -- needs some more work

  // these are sharding agnostic
  def persistenceIdFrom(persistentEntityId: String): PersistenceId =
    PersistenceIds.asLagomScala(journalName, persistentEntityId)

  def behavior(persistentId: PersistenceId): EventSourcedBehavior[GreetingsCommand,
                                                           GreetingsChanged,
                                                           GreetingsState] = {
    EventSourcedBehavior.withEnforcedReplies(
      persistenceId = persistenceIdFrom(persistentId),
      emptyState = GreetingsState.empty,
      commandHandler =
        (state, command) => GreetingsState.applyCommand(state, command),
      eventHandler = (state, evt) => GreetingsState.applyEvent(state, evt)
    )
  }
}

// Only added here for historical reasons. This was the starting point. This implementation is no longer useful.
object GreetingsPersistentEntity1 {

  def shardedBehavior(
    entityContext: EntityContext
  ): EventSourcedBehavior[GreetingsCommand, GreetingsChanged, GreetingsState] =
    // Note that `entityContext.entityId` is the sharding entity id (which happens to be "prefix|id")
    EventSourcedBehavior.withEnforcedReplies(
      persistenceId = PersistenceId(entityContext.entityId),
      emptyState = GreetingsState.empty,
      commandHandler =
        (state, command) => GreetingsState.applyCommand(state, command),
      eventHandler = (state, evt) => GreetingsState.applyEvent(state, evt)
    )

}

// Could be moved to Akka
object PersistenceIds {
  def asLagomScala(namespace: String,
                   persistenEntityId: String): PersistenceId =
    custom(namespace, "|", persistenEntityId)

  def asLagomJava(namespace: String, persistenEntityId: String): PersistenceId =
    custom(namespace, "", persistenEntityId)

  def custom(namespace: String,
             separator: String,
             persistenEntityId: String): PersistenceId =
    PersistenceId(s"$namespace$separator$persistenEntityId")
}

final object GreetingsState {
  val empty = GreetingsState("Hello, ")

  def applyCommand(
    state: GreetingsState,
    cmd: GreetingsCommand
  ): ReplyEffect[GreetingsChanged, GreetingsState] = {
    cmd match {
      case gg: GetGreetings =>
        Effect.reply(cmd) { Confirmed(state.message) }
      case ug: UpdateGreetings =>
        Effect
          .persist(GreetingsChanged(ug.message))
          .thenReply(cmd) { _ =>
            Confirmed(ug.message)
          }
    }
  }

  def applyEvent(state: GreetingsState, evt: GreetingsChanged): GreetingsState =
    GreetingsState(evt.message)
}
final case class GreetingsState(message: String) extends JacksonSerializable

sealed trait GreetingsCommand
    extends ExpectingReply[GreetingsCommandReply]
    with JacksonSerializable
final case class UpdateGreetings(message: String)(
  override val replyTo: ActorRef[GreetingsCommandReply]
) extends GreetingsCommand
final case class GetGreetings(
  override val replyTo: ActorRef[GreetingsCommandReply]
) extends GreetingsCommand

sealed trait GreetingsCommandReply extends JacksonSerializable
final case class Confirmed(message: String) extends GreetingsCommandReply
final case class Rejected(cause: String) extends GreetingsCommandReply

final case class GreetingsChanged(message: String) extends JacksonSerializable

trait JacksonSerializable
