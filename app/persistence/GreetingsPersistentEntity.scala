package persistence

import akka.actor.typed.ActorRef
import akka.persistence.typed.ExpectingReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.Effect
import akka.persistence.typed.scaladsl.EventSourcedBehavior
import akka.persistence.typed.scaladsl.ReplyEffect
import persistence.akkalibs.PersistenceIds

class GreetingsPersistentEntity(val journalName: String) {

  def serializeArguments(persistenceId: String):String = s"shard-name-$journalName-$persistenceId"
  def deserializeArguments(entityId: String):String = entityId.split("-").drop(3).mkString("-")

  // this is sharding agnostic
  def persistenceIdFrom(persistentEntityId: String): PersistenceId =
    PersistenceIds.asLagomScala(journalName, persistentEntityId)

  // this is sharding agnostic
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
