package com.example.graphdb.http

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import com.example.graphdb.graph.GraphNodeEntity.NodeId
import com.example.graphdb.graph.GraphContext._
import com.example.graphdb.graph.{GraphContext, GraphDSL}
import spray.json.DefaultJsonProtocol._
import com.example.graphdb.graph.ValueInstances._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object GraphDbRoutes {

  private def convertToValue(attributeType: String, value: String): Value =
    attributeType match {
      case "string"  => StringValue(value)
      case "boolean" => BoolValue(value.toBoolean)
      case "number"  => NumberValue(value.toDouble)
    }

  final case class AddNode(nodeType: String, properties: Seq[UpdateField]) {

    def fieldToValue: Seq[(String, Value)] =
      this.properties.map { attribute =>
        (attribute.name, convertToValue(attribute.attributeType, attribute.value))
      }
  }

  final case class UpdateField(attributeType: String, name: String, value: String) {
    def fieldToValue: Value = convertToValue(this.attributeType, this.value)

  }

  final case class RemoveField(name: String)

  final case class RemoveRelationBody(toId: String)

  final case class UpdateOneToOneRel(relationType: String, toId: NodeId)

  final case class UpdateOneToManyRel(name: String, nodeIds: Set[NodeId])

}

class GraphDbRoutes()(private implicit val system: ActorSystem[_], private implicit val graphContext: GraphContext)
    extends GraphDSL {
  private implicit val ec: ExecutionContext = system.executionContext

  private val graph = new GraphDSLImpl()

  import JsonFormats._
  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import akka.http.scaladsl.server.Directives._

  val route: Route =
    path("nodes") {
      post {
        entity(as[GraphDbRoutes.AddNode]) { data =>
          onSuccess((graph += data.nodeType within (data.fieldToValue: _*))) { nodeId =>
            complete(StatusCodes.Created -> nodeId)
          }
        }
      }
    } ~ path("nodes") {
      get {
        onSuccess(graph.getAllNodes.map(_.nodes)) { reply =>
          complete(reply)
        }
      }
    } ~ path("nodes" / "relations") {
      parameters(
        (
          "nodeType".as[String],
          "attributeName".as[String],
          "attributeValue".as[String],
          "relationType".as[String]
        )
      ) { (nodeType, attributeName, attributeValue, relationType) =>
        get {
          import graph._
          val reply: Future[Set[NodeId]] = for {
            nodes <- graph.getNodesByType(nodeType).map(_.nodes)
            res = nodes
              .filter(_ hasAttr attributeName)
              .filterNot(
                node => node.attr(attributeValue).isInstanceOf[EmptyValue]
              )
              .map(_ relation relationType)
          } yield res.flatten

          complete(reply)
        }
      }
    } ~ path("nodes" / Segment / "relations") { id =>
      get {
        parameter("relation".as[String]) { relation =>
          onSuccess(graph.relationIds(id, relation)) { reply =>
            complete(StatusCodes.OK -> reply)
          }
        }
      }
    } ~ path("nodes" / Segment) { nodeId: String =>
      get {
        onComplete(graph.nodeById(nodeId)) {
          case Success(node) => complete(node)
          case Failure(ex)   => complete(StatusCodes.NotFound -> ex.getMessage)
        }
      }
    } ~ path("nodes" / "fields" / Segment) { nodeId =>
      put {
        entity(as[GraphDbRoutes.UpdateField]) { data =>
          import com.example.graphdb.graph.ValueInstances._
          import graph._

          onSuccess(graph(nodeId) += data.name -> data.fieldToValue) {
            complete(StatusCodes.OK)
          }
        }
      }
    } ~ path("nodes" / "fields" / Segment) { nodeId =>
      delete {
        entity(as[GraphDbRoutes.RemoveField]) { data =>
          onSuccess(graph.removeAttr(nodeId, data.name)) {
            complete(StatusCodes.OK)
          }
        }
      }
    } ~ path("nodes" / "relations" / Segment) { nodeId =>
      put {
        import graph._

        entity(as[GraphDbRoutes.UpdateOneToOneRel]) { data =>
          onSuccess(graph(nodeId) ~> data.relationType ~> graph(data.toId)) {
            complete(StatusCodes.OK)
          }
        }
      }
    } ~
      path("nodes" / "relations" / Segment) { nodeId =>
        delete {
          entity(as[GraphDbRoutes.RemoveRelationBody]) { data =>
            onSuccess(graph.removeRelation(nodeId, data.toId)) {
              complete(StatusCodes.OK)
            }
          }
        }
      }
}
