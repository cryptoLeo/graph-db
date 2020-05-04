package com.example.graphdb.http

import com.example.graphdb.graph.GraphContext._
import com.example.graphdb.graph.GraphNodeEntity._
import spray.json.DefaultJsonProtocol._
import spray.json.{JsBoolean, JsNumber, JsObject, JsString, JsValue, RootJsonFormat}

object JsonFormats {

  implicit val updateFieldFormat: RootJsonFormat[GraphDbRoutes.UpdateField] =
    jsonFormat3(GraphDbRoutes.UpdateField)
  implicit val addNodeFormat: RootJsonFormat[GraphDbRoutes.AddNode] =
    jsonFormat2(GraphDbRoutes.AddNode)
  implicit val removeFieldFormat: RootJsonFormat[GraphDbRoutes.RemoveField] =
    jsonFormat1(GraphDbRoutes.RemoveField)
  implicit val updateOneToOneFormat: RootJsonFormat[GraphDbRoutes.UpdateOneToOneRel] =
    jsonFormat2(GraphDbRoutes.UpdateOneToOneRel)
  implicit val removeRelationFormat: RootJsonFormat[GraphDbRoutes.RemoveRelationBody] =
    jsonFormat1(GraphDbRoutes.RemoveRelationBody)
  implicit object FieldsJsonFormat extends RootJsonFormat[Fields] {
    def write(value: Fields): JsObject =
      JsObject(value.view.mapValues {
        case StringValue(value) => JsString(value)
        case NumberValue(value) => JsNumber(value)
        case BoolValue(value)   => JsBoolean(value)
      }.toMap)
    def read(value: JsValue): Fields = ???
  }

  implicit val StateFormat: RootJsonFormat[GraphNodeState] = jsonFormat4(GraphNodeState)
}
