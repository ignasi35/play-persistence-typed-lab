package persistence

import akka.actor.typed.ActorRef
import akka.cluster.sharding.typed.scaladsl.EntityContext
import akka.persistence.typed.ExpectingReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.EventSourcedBehavior

object GreetingsPersistentEntity {

  def behavior(
    entityContext: EntityContext
  ): EventSourcedBehavior[GreetingsCommand, GreetingsChanged, GreetingsState] =
    EventSourcedBehavior
      .withEnforcedReplies[GreetingsCommand, GreetingsChanged, GreetingsState](
        persistenceId = PersistenceId("abc"),
        emptyState = GreetingsState.empty,
        commandHandler = (state, cmd) =>
          throw new RuntimeException(
            "TODO: process the command & return an Effect"
        ),
        eventHandler = (state, evt) =>
          throw new RuntimeException(
            "TODO: process the event return the next state"
        )
      )

}

sealed trait GreetingsCommand extends ExpectingReply[GreetingsCommandReply]
final case class UpdateGreetings(message: String)(
  override val replyTo: ActorRef[GreetingsCommandReply]
) extends GreetingsCommand
final case class GetGreetings(
  override val replyTo: ActorRef[GreetingsCommandReply]
) extends GreetingsCommand

sealed trait GreetingsCommandReply
final case class Confirmed(message: String) extends GreetingsCommandReply
final case class Rejected(cause: String) extends GreetingsCommandReply

final case class GreetingsChanged(id: String, message: String)
final case class GreetingsState(message: String)
final object GreetingsState {
  val empty = GreetingsState("Hello, ")
}
