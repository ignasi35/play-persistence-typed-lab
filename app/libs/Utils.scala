package libs

import akka.persistence.typed.PersistenceId

// Utilities to build PersistenceIds
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

// Utilities for tagging
object ProjectionTaggers {

  // A Tagger that given a PEId produces a sharded tag. This is necessary to
  // build projections over this journal
  final def lagomProjectionsTagger(
      taggerPrefix: String,
      numOfTagShards: Int
  ): PersistenceId => String = { persistenceId =>
    val shardNum = Math.abs(persistenceId.id.hashCode % numOfTagShards)
    s"$taggerPrefix$shardNum"
  }

}
