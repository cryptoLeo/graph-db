package com.example.graphdb.graph

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.util.Timeout
import com.example.graphdb.graph.GraphContext.{Field, Value}
import com.example.graphdb.graph.GraphNodeEntity._
import com.example.graphdb.readside.GraphNodeRepository
import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}

import scala.concurrent.{ExecutionContext, Future}


object GraphContext {

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(
    Array(
      new JsonSubTypes.Type(value = classOf[StringValue], name = "string"),
      new JsonSubTypes.Type(value = classOf[BoolValue], name = "boolean"),
      new JsonSubTypes.Type(value = classOf[NumberValue], name = "number"),
      new JsonSubTypes.Type(value = classOf[EmptyValue], name = "empty"),
      new JsonSubTypes.Type(value = classOf[Field], name = "field"),
      new JsonSubTypes.Type(value = classOf[ListValue], name = "list")
    )
  )
  sealed trait Value {
    def apply(graphNode: GraphNodeState): Value = this
  }

  case class NodeRef(nodeId: NodeId)

  sealed case class GraphNodes(nodes: Set[GraphNodeState]) {
    def nodeIds: Set[NodeId] = nodes.map(_.nodeId)
  }

  case class EmptyValue() extends Value

  case class Field(name: String) extends Value {
    override def apply(graphNode: GraphNodeState): Value = {
      if (graphNode.properties.contains(name))
        graphNode.properties(name)
      else EmptyValue()
    }
  }

  case class BoolValue(value: Boolean) extends Value

  case class NumberValue(value: Double) extends Value

  case class StringValue(value: String) extends Value

  case class ListValue(values: Set[Value]) extends Value

}

class GraphContext(implicit private val system: ActorSystem[_],
                   sharding: ClusterSharding) {
  private val graphNodeRepository: GraphNodeRepository = new GraphNodeRepository

  implicit private val timeout: Timeout =
    Timeout.create(system.settings.config.getDuration("askTimeout"))
  implicit val ec: ExecutionContext = system.executionContext

   def nodeByIds(ids: Set[NodeId]): Future[Set[GraphNodeState]] =
    graphNodeRepository.getByIds(ids)

   def getAllNodes: Future[Set[GraphNodeState]] =
    graphNodeRepository.getAllNodes.runFold(Set.empty[GraphNodeState])(_ + _)

   def filter(func: GraphNodeState => Boolean): Future[Set[GraphNodeState]] =
    graphNodeRepository.getAllNodes
      .filter(func)
      .runFold(Set.empty[GraphNodeState])(_ + _)

   def filter(nodeType: NodeType,
                      relation: HasRelation,
                      field: Field,
                      fieldValue: Value): Future[Set[GraphNodeState]] =
    graphNodeRepository
      .getByTypeAndFieldAndRelation(
        nodeType,
        field.name,
        fieldValue,
        relation.relationType,
        relation.relationId
      )
      .runFold(Set.empty[GraphNodeState])(_ + _)

    def filter(nodeType: NodeType,
                      relation: HasRelation): Future[Set[GraphNodeState]] =
    graphNodeRepository
      .getByTypeAndRelation(
        nodeType,
        relation.relationType,
        relation.relationId
      )
      .runFold(Set.empty[GraphNodeState])(_ + _)

  def filter(nodeType: NodeType,
                      relationType: RelationType): Future[Set[GraphNodeState]] =
    graphNodeRepository
      .getByTypeAndRelationType(nodeType, relationType)
      .runFold(Set.empty[GraphNodeState])(_ + _)

  def filter(nodeType: NodeType,
                      field: Field,
                      fieldValue: Value): Future[Set[GraphNodeState]] =
    graphNodeRepository
      .getByTypeAndField(nodeType, field.name, fieldValue)
      .runFold(Set.empty[GraphNodeState])(_ + _)

  def filterNotField(nodeType: NodeType,
                              field: Field,
                              fieldValue: Value): Future[Set[GraphNodeState]] =
    graphNodeRepository
      .getByTypeAndNotField(nodeType, field.name, fieldValue)
      .runFold(Set.empty[GraphNodeState])(_ + _)

  def filter(nodeId: NodeId): Future[Set[GraphNodeState]] =
    nodeById(nodeId).map(Set(_))

  def nodeById(id: NodeId): Future[GraphNodeState] =
    graphNodeRepository.getNode(id).flatMap {
      case Some(node) => Future.successful(node)
      case None       => Future.failed(new Exception(s"Node $id does not exist"))
    }

  def createNode(id: NodeId, nodeType: NodeType): Future[Unit] = {
    val entityRef = sharding.entityRefFor(GraphNodeEntity.EntityKey, id)
    entityRef.ask[Reply](Create(id, nodeType, Map.empty, _)).map(_ => ())
  }

  def addAttr(id: NodeId, name: String, value: Value): Future[Unit] = {
    val entityRef = sharding.entityRefFor(GraphNodeEntity.EntityKey, id)
    entityRef
      .ask[Reply](AddField(name, value, _))
      .map(_ => ())
  }

  def addAttrs(id: NodeId,
                        attrs: Set[(String, Value)]): Future[Unit] = {
    val entityRef = sharding.entityRefFor(GraphNodeEntity.EntityKey, id)
    val attrMap = attrs.map(v => v._1 -> v._2).toMap
    entityRef
      .ask[Reply](AddFields(attrMap, _))
      .map(_ => ())
  }

  def removeAttr(id: NodeId, name: String): Future[Unit] = {
    val entityRef = sharding.entityRefFor(GraphNodeEntity.EntityKey, id)
    entityRef
      .ask[Reply](RemoveField(name, _))
      .map(_ => ())
  }

  def addRelation(id: NodeId, toId: NodeId): Future[Unit] = {
    val entityRef = sharding.entityRefFor(GraphNodeEntity.EntityKey, id)
    entityRef
      .ask[Reply](UpdateRelation("default", toId, _))
      .map(_ => ())
  }

  def addRelation(id: NodeId,
                           relationType: RelationType,
                           toId: NodeId): Future[Unit] = {
    val entityRef = sharding.entityRefFor(GraphNodeEntity.EntityKey, id)
    entityRef
      .ask[Reply](UpdateRelation(relationType, toId, _))
      .map(_ => ())
  }

  def removeRelation(id: NodeId, toId: NodeId): Future[Unit] = {
    val entityRef = sharding.entityRefFor(GraphNodeEntity.EntityKey, id)
    entityRef
      .ask[Reply](RemoveRelation(id, toId, _))
      .map(_ => ())
  }

  def filterByNodeType(nodeType: NodeType): Future[Set[GraphNodeState]] = {
    graphNodeRepository
      .getByType(nodeType)
      .runFold(Set.empty[GraphNodeState])(_ + _)
  }

  def filterByNodeTypes(
    nodeTypes: Set[NodeType]
  ): Future[Set[GraphNodeState]] = {
    graphNodeRepository
      .getByNodeTypes(nodeTypes)
      .runFold(Set.empty[GraphNodeState])(_ + _)
  }

}
