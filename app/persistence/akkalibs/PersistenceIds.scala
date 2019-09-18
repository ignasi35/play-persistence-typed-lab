package persistence.akkalibs

import akka.persistence.typed.PersistenceId

/**
 * 
 */
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
