package com.example.graphdb.graph

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import com.example.graphdb.CborSerializable
import com.example.graphdb.graph.GraphContext.Value

object GraphNodeEntity {

  type NodeId = String
  type NodeType = String
  type FieldType = String
  type Fields = Map[FieldType, Value]
  type RelationType = String

  val EntityKey: EntityTypeKey[Command] = EntityTypeKey[Command]("Node")

  final case class GraphNodeState(nodeId: NodeId,
                                  nodeType: NodeType,
                                  properties: Fields = Map(),
                                  relations: Map[NodeId, RelationType] = Map())

  def empty: GraphNodeState = GraphNodeState(null, null)

  def apply(entityId: String,
            eventProcessorTags: Set[String]): Behavior[Command] = {
    Behaviors.setup { ctx =>
      EventSourcedBehavior[Command, Event, GraphNodeState](
        PersistenceId("Node", entityId),
        empty,
        (state, command) => commandHandler(ctx, state, command),
        (state, event) => eventHandler(ctx, state, event)
      ).withTagger(_ => eventProcessorTags)
    }
  }

  def commandHandler(ctx: ActorContext[Command],
                     state: GraphNodeState,
                     command: Command): Effect[Event, GraphNodeState] = {
    def ifStateNotEmpty(
                         replyTo: ActorRef[Reply]
                       )(effect: => Effect[Event, GraphNodeState]) =
      if (state.nodeId != null)
        effect
      else
        Effect.none.thenReply(replyTo)((_: GraphNodeState) => EmptyNodeReply)

    command match {

      case Create(id, name, fields, replyTo) =>
        if (state.nodeId == null)
          Effect
            .persist(GraphNodeCreated(id, name, fields))
            .thenRun(node => replyTo ! SuccessReply(node))
        else
          Effect.reply(replyTo)(NodeIsAlreadyCreated)

      case AddFields(fields, replyTo) =>
        ifStateNotEmpty(replyTo) {
          Effect
            .persist(FieldsAdded(state.nodeId, fields))
            .thenReply(replyTo)(entity => SuccessReply(entity))
        }

      case AddField(name, value, replyTo) =>
        ifStateNotEmpty(replyTo) {
          Effect
            .persist(FieldAdded(state.nodeId, name, value))
            .thenReply(replyTo)(entity => SuccessReply(entity))
        }

      case UpdateRelation(relationType, toId, replyTo) =>
        ifStateNotEmpty(replyTo) {
          Effect
            .persist(RelationUpdated(state.nodeId, relationType, toId))
            .thenReply(replyTo)(entity => SuccessReply(entity))
        }

      case RemoveField(field, replyTo) =>
        ifStateNotEmpty(replyTo) {
          Effect
            .persist(FieldRemoved(state.nodeId, field))
            .thenReply(replyTo)(entity => SuccessReply(entity))
        }

      case RemoveRelation(nodeId, toId, replyTo) =>
        ifStateNotEmpty(replyTo) {
          Effect
            .persist(RelationRemoved(nodeId, toId))
            .thenReply(replyTo)(entity => SuccessReply(entity))
        }

      case Get(replyTo) =>
        ifStateNotEmpty(replyTo) {
          Effect.reply(replyTo)(SuccessReply(state))
        }

      case _ =>
        Effect.none
    }
  }

  def eventHandler(ctx: ActorContext[Command],
                   state: GraphNodeState,
                   event: Event): GraphNodeState = event match {

    case GraphNodeCreated(id, entityType, fields) =>
      state.copy(nodeId = id, nodeType = entityType, properties = fields)

    case FieldsAdded(_, fields) =>
      state.copy(properties = state.properties ++ fields)

    case FieldAdded(_, name, value) =>
      state.copy(properties = state.properties + (name -> value))

    case RelationUpdated(_, relationType, toId) =>
      state.copy(relations = state.relations + (toId -> relationType))

    case RelationRemoved(_, toId) =>
      state.copy(relations = state.relations - toId)

    case FieldRemoved(_, field) =>
      state.copy(properties = state.properties - field)

    case _ => state
  }

  val EmptyNodeReply: ReplyError = ReplyError("Node is not created!")
  val NodeIsAlreadyCreated: ReplyError = ReplyError("Node is already created!")
  val NodeNotCreatedReply: ReplyError = ReplyError("Node is not created!")


  trait Command extends CborSerializable

  final case class Create(id: NodeId,
                          name: NodeType,
                          fields: Map[FieldType, Value],
                          replyTo: ActorRef[Reply]) extends Command

  final case class AddField(name: String, value: Value, replyTo: ActorRef[Reply]) extends Command

  final case class AddFields(fields: Map[String, Value], replyTo: ActorRef[Reply]) extends Command

  final case class RemoveField(field: String, replyTo: ActorRef[Reply]) extends Command

  final case class Get(replyTo: ActorRef[Reply]) extends Command

  final case class UpdateRelation(nodeType: RelationType,
                                  toId: NodeId,
                                  replyTo: ActorRef[Reply]) extends Command

  final case class RemoveRelation(nodeId: NodeId,
                                  toId: NodeId,
                                  replyTo: ActorRef[Reply]) extends Command



  sealed trait Event extends CborSerializable

  final case class RelationUpdated(id: NodeId, nodeType: RelationType, toId: NodeId) extends Event

  final case class RelationRemoved(id: NodeId, toId: NodeId) extends Event

  final case class GraphNodeCreated(id: NodeId,
                                    entityType: NodeType,
                                    fields: Map[FieldType, Value]) extends Event

  final case class FieldsAdded(id: NodeId, fields: Map[FieldType, Value]) extends Event

  final case class FieldAdded(id: NodeId, name: String, value: Value) extends Event

  final case class FieldRemoved(id: NodeId, field: String) extends Event





  trait Reply extends CborSerializable

  final case class ReplyError(message: String) extends Reply

  final case class SuccessReply(state: GraphNodeEntity.GraphNodeState) extends Reply

}
