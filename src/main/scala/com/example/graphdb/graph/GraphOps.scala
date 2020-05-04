package com.example.graphdb.graph

import GraphNodeEntity.{NodeId, GraphNodeState, NodeType, RelationType}

trait GraphOps {
  import GraphContext._

  sealed trait Predicate {
    def apply(graphNode: GraphNodeState): Boolean
  }

  case class Func(func: GraphNodeState => Boolean) extends Predicate {
    override def apply(graphNode: GraphNodeState): Boolean = func(graphNode)
  }

  case class Or(x: Predicate, y: Predicate) extends Predicate {
    override def apply(graphNode: GraphNodeState): Boolean =
      x(graphNode) || y(graphNode)
  }

  case class And(x: Predicate, y: Predicate) extends Predicate {
    override def apply(graphNode: GraphNodeState): Boolean =
      x(graphNode) && y(graphNode)
  }

  case class Not(x: Predicate) extends Predicate {
    override def apply(graphNode: GraphNodeState): Boolean = !x(graphNode)
  }

  case class Eq(x: Value, y: Value) extends Predicate {
    override def apply(graphNode: GraphNodeState): Boolean =
      x(graphNode) == y(graphNode)
  }

  case class HasNodeType(nodeType: NodeType) extends Predicate {
    override def apply(graphNode: GraphNodeState): Boolean =
      graphNode.nodeType == nodeType
  }

  case class HasRelation(relationType: RelationType, relationId: NodeId) extends Predicate {
    override def apply(graphNode: GraphNodeState): Boolean =
      graphNode.relations.get(relationId).contains(relationType)
  }

  case class HasRelationId(relationId: NodeId) extends Predicate {
    override def apply(graphNode: GraphNodeState): Boolean =
      graphNode.relations.contains(relationId)
  }

  case class HasRelationType(relationType: RelationType) extends Predicate {
    override def apply(graphNode: GraphNodeState): Boolean =
      graphNode.relations.exists {
        case (_: NodeId, tp: NodeType) => tp == relationType
      }
  }

  implicit class PredicateOps(predicate: Predicate) {
    def and(pred: Predicate): And = And(predicate, pred)
  }

}
