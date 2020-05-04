package com.example.graphdb.readside

import akka.Done
import akka.actor.typed.ActorSystem
import akka.actor.typed.eventstream.EventStream
import akka.persistence.typed.PersistenceId
import akka.stream.alpakka.cassandra.scaladsl.CassandraSessionRegistry
import com.example.graphdb.EventProcessorStream
import com.example.graphdb.graph.GraphNodeEntity._
import com.example.graphdb.graph.ValueInstances._

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._




class GraphEventProcessorStream(system: ActorSystem[_],
                                executionContext: ExecutionContext,
                                eventProcessorId: String,
                                tag: String)
    extends EventProcessorStream(
      system,
      executionContext,
      eventProcessorId,
      tag
    ) {

  private val session =
    CassandraSessionRegistry(system).sessionFor("alpakka.cassandra")

  def processEvent(event: Event,
                   persistenceId: PersistenceId,
                   sequenceNr: Long): Future[Done] = {
    log.info(
      "EventProcessor({}) consumed {} from {} with seqNr {}",
      tag,
      event,
      persistenceId,
      sequenceNr
    )

    val res = event match {
      case GraphNodeCreated(id, entityType, fields) =>
        session.executeWrite(
          "INSERT INTO akka.nodes (type, nodeId, properties) VALUES (?, ?, ?)",
          entityType,
          id,
          fields.view.mapValues(valueToString).toMap.asJava
        )
      case RelationUpdated(id, name, nodeId) =>
        session.executeWrite(
          "UPDATE akka.nodes SET relations += ? WHERE nodeId = ?",
          Map(nodeId -> name).asJava,
          id
        )
      case FieldsAdded(id, fields) =>
        session.executeWrite(
          "UPDATE akka.nodes SET properties += ? WHERE nodeId = ?",
          fields.map(map => (map._1 -> valueToString(map._2))).asJava,
          id
        )
      case FieldAdded(id, name, value) =>
        session.executeWrite(
          "UPDATE akka.nodes SET properties += ? WHERE nodeId = ?",
          Map(name -> valueToString(value)).asJava,
          id
        )

      case FieldRemoved(id, field) =>
        session.executeWrite(
          "DELETE properties[?] FROM akka.nodes WHERE nodeId = ?",
          field,
          id
        )

      case RelationRemoved(id, toId) =>
        session.executeWrite(
          "DELETE relations[?] FROM akka.nodes WHERE nodeId = ?",
          toId,
          id
        )

      case _ =>
        log.warn("Unknown event has been received")
        Future.successful(Done)
    }

    system.eventStream ! EventStream.Publish(event)
    res
  }
}
