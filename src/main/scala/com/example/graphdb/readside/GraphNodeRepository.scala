package com.example.graphdb.readside

import akka.NotUsed
import akka.actor.typed.ActorSystem
import akka.stream.alpakka.cassandra.scaladsl.CassandraSessionRegistry
import akka.stream.scaladsl.Source
import com.example.graphdb.graph.GraphNodeEntity._
import com.example.graphdb.graph.ValueInstances._
import com.datastax.oss.driver.api.core.cql.Row
import com.example.graphdb.graph.GraphNodeEntity.GraphNodeState
import com.example.graphdb.graph.GraphContext.Value

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

class GraphNodeRepository(implicit private val system: ActorSystem[Nothing]) {
  private implicit val ec: ExecutionContext = system.executionContext
  private val session =
    CassandraSessionRegistry(system).sessionFor("alpakka.cassandra")

  private def rowToNodeState(row: Row): GraphNodeState =
    GraphNodeState(
      nodeId = row.getString("nodeId"),
      nodeType = row.getString("type"),
      properties = row
        .getMap("properties", classOf[FieldType], classOf[String])
        .asScala
        .view
        .mapValues(stringToValue)
        .toMap,
      relations = row
        .getMap("relations", classOf[String], classOf[String])
        .asScala
        .toMap,
    )

  def getNode(nodeId: NodeId): Future[Option[GraphNodeState]] =
    session
      .selectOne("SELECT * FROM akka.nodes WHERE nodeId = ?", nodeId)
      .map(opt => opt map rowToNodeState)

  def getByIds(nodeIds: Set[NodeId]): Future[Set[GraphNodeState]] =
    session
      .selectAll(
        "SELECT * FROM akka.nodes WHERE nodeId IN (%s)"
          .format(nodeIds.mkString("'", "', '", "'"))
      )
      .map(_.map(rowToNodeState).toSet)

  def getAllNodes: Source[GraphNodeState, NotUsed] =
    session
      .select("SELECT * FROM akka.nodes")
      .map(rowToNodeState)

  def getAllWithRelation(
      relationType: RelationType): Source[GraphNodeState, NotUsed] =
    session
      .select(
        "SELECT * FROM akka.nodes WHERE relations CONTAINS KEY ? ALLOW FILTERING",
        relationType
      )
      .map(rowToNodeState)

  def getByType(nodeType: NodeType): Source[GraphNodeState, NotUsed] =
    session
      .select(
        "SELECT * FROM akka.nodes WHERE type = ? ALLOW FILTERING",
        nodeType
      )
      .map(rowToNodeState)

  def getByNodeTypes(types: Set[NodeType]): Source[GraphNodeState, NotUsed] =
    session
      .select(
        "SELECT * FROM akka.nodes WHERE type IN (%s) ALLOW FILTERING".format(types.mkString("'", "', '", "'"))
      )
      .map(rowToNodeState)

  def getByTypeAndField(nodeType: NodeType,
                        fieldName: FieldType,
                        fieldValue: Value): Source[GraphNodeState, NotUsed] =
    session
      .select(
        "SELECT * FROM akka.nodes WHERE type = ? AND attributes[?] = ? ALLOW FILTERING",
        nodeType,
        fieldName,
        valueToString(fieldValue)
      )
      .map(rowToNodeState)

  def getByTypeAndNotField(nodeType: NodeType,
                           fieldName: FieldType,
                           fieldValue: Value): Source[GraphNodeState, NotUsed] =
    session
      .select(
        "SELECT * FROM akka.nodes WHERE type = ? AND attributes[?] <> ? ALLOW FILTERING",
        nodeType,
        fieldName,
        valueToString(fieldValue)
      )
      .map(rowToNodeState)

  def getByTypeAndRelationType(
      nodeType: NodeType,
      relationType: RelationType
  ): Source[GraphNodeState, NotUsed] =
    session
      .select(
        "SELECT * FROM akka.nodes WHERE type = ? AND relations CONTAINS KEY ? ALLOW FILTERING",
        nodeType,
        relationType
      )
      .map(rowToNodeState)

  def getByTypeAndFieldAndRelation(
      nodeType: NodeType,
      fieldName: FieldType,
      fieldValue: Value,
      relationType: RelationType,
      relationId: NodeId): Source[GraphNodeState, NotUsed] =
    session
      .select(
        "SELECT * FROM akka.nodes WHERE type = ? AND attributes[?] = ? AND relations[?] = ? ALLOW FILTERING",
        nodeType,
        fieldName,
        valueToString(fieldValue),
        relationType,
        relationId
      )
      .map(rowToNodeState)

  def getByTypeAndRelation(nodeType: NodeType,
                           relationType: RelationType,
                           relationId: NodeId): Source[GraphNodeState, NotUsed] =
    session
      .select(
        "SELECT * FROM akka.nodes WHERE type = ? AND attributes[?] = ? AND relations[?] = ? ALLOW FILTERING",
        nodeType,
        relationType,
        relationId
      )
      .map(rowToNodeState)

}
