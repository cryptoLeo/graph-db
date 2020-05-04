package com.example.graphdb.graph

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import com.example.graphdb.{EventProcessor, EventProcessorSettings, Main}
import com.example.graphdb.graph.GraphContext._
import com.example.graphdb.graph.GraphNodeEntity.NodeId
import com.example.graphdb.readside.GraphEventProcessorStream
import com.typesafe.config.ConfigFactory
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.language.postfixOps

class GraphDSLSpec
  extends ScalaTestWithActorTestKit(ConfigFactory.load())
    with AnyWordSpecLike  {

  implicit val ex: ExecutionContextExecutor = system.executionContext
  implicit val sharding = {
    val sharding = ClusterSharding(system)
    val eventProcessorSettings = EventProcessorSettings(system)


    sharding.init(Entity(GraphNodeEntity.EntityKey) { entityContext =>
      val n = math.abs(
        entityContext.entityId.hashCode % eventProcessorSettings.parallelism
      )
      val eventProcessorTag = eventProcessorSettings.tagPrefix + "-" + n

      GraphNodeEntity(entityContext.entityId, Set(eventProcessorTag))
    }.withRole("write-model"))

    EventProcessor.init(
      system,
      eventProcessorSettings,
      tag =>
        new GraphEventProcessorStream(
          system,
          ex,
          eventProcessorSettings.id,
          tag
        )
    )

    sharding

  }
  implicit val graphContext: GraphContext = new GraphContext
  val graph = new GraphDSLImpl()

  
  import com.example.graphdb.graph.ValueInstances._


  "General GraphDSL" should {

    "Create a data node of a given type" in  {

      import graph._

      for {
        nodeId <- graph += "Person"
      } yield {
        whenReady(graph(nodeId) nodeType)(_ shouldEqual "Person")
      }
    }

    "Add a new attribute to an existing node" in  {

      import graph._

      for {
        nodeId <- graph += "Person"
        _ <- graph(nodeId) += "name" -> "value"
      } yield {
        whenReady(graph(nodeId) attr "name")(_ shouldEqual StringValue("value"))
      }
    }

    "Create a data node with attributes" in {

      import graph._

      for {
        nodeId <- graph += "Person" within("name" -> "value", "city" -> "Seattle")
      } yield {
        whenReady(graph(nodeId) attr "name")(_ shouldEqual StringValue("value"))
        whenReady(graph(nodeId) attr "city")(_ shouldEqual StringValue("Seattle"))
      }
    }

    "Delete an attribute from an existing node" in {

      import graph._


      for {
        nodeId <- graph += "Person"
        _ <- graph(nodeId) += "blahblah" -> "value1"
        _ <- graph(nodeId) += "blahblah2" -> "value2"
        _ <- graph(nodeId) -= "blahblah"
      } yield {
        whenReady(graph(nodeId) hasAttr "blahblah")(_ shouldEqual false)
        whenReady(graph(nodeId) attr "blahblah2")(_ shouldEqual StringValue("value2"))
      }
    }

    "Establish a directional link between a two nodes in the system" in {

      import graph._

      for {
        nodeId1 <- graph += "Person"
        nodeId2 <- graph += "Person"
        _ <- graph(nodeId1) ~> graph(nodeId2)
      } yield {
        whenReady(graph(nodeId1) hasRelation nodeId2)(_ shouldEqual true)
        whenReady(graph(nodeId2) hasRelation nodeId1)(_ shouldEqual false)
      }
    }
  }

  "API querying Predicate" should {
    "OR(X, Y) where X, Y are Predicate" in {

      import graph._

      for {
        _ <- graph += "Person"
        _ <- graph += "Business"

      } yield {
        whenReady(graph find Or(HasNodeType("Person"), HasNodeType("Business")))(
          _.nodes.map(_.nodeType) shouldEqual Set("Person", "Business"))
      }
    }

    "AND(X, Y) where X, Y are Predicate" in {

      import graph._

      for {
        personId1 <- graph += "Person"
        personId2 <- graph += "Person"
        _ <- graph(personId1) += "city" -> "Tokyo"
        _ <- graph(personId2) += "city" -> "Sydney"
      } yield {
        whenReady(graph find And(HasNodeType("Person"), Eq(Field("city"), StringValue("Tokyo"))))(
          _.nodes.map(_.attr("city")) shouldEqual Set(StringValue("Tokyo")))
      }

    }

    "NOT(X) where X is Predicate" in {

      import graph._

      for {
        personId1 <- graph += "Person"
        personId2 <- graph += "Person"
        _ <- graph(personId1) += "city" -> "Tokyo"
        _ <- graph(personId2) += "city" -> "Sydney"
      } yield {
        whenReady(graph find And(HasNodeType("Person"), Not(Eq(Field("city"), StringValue("Sydney")))))(
          _.nodes.map(_.attr("city")) shouldEqual Set(StringValue("Sydney")))
      }
    }

    "EQ(X, Y) where X, Y are Value" in {

      import graph._

      for {
        personId1 <- graph += "Person"
        personId2 <- graph += "Person"
        _ <- graph(personId1) += "city" -> "Tokyo"
        _ <- graph(personId2) += "city" -> "Sydney"
      } yield {
        whenReady(graph find And(HasNodeType("Person"), Eq(Field("city"), StringValue("Sydney"))))(
          _.nodes.map(_.attr("city")) shouldEqual Set(StringValue("Sydney")))
      }
    }

    "HAS_RELATION(X) where X is a Relation ID" in {

      import graph._

      for {
        personId1 <- graph += "Person"
        personId2 <- graph += "Person"
        _ <- graph(personId1) += "city" -> "Tokyo"
        _ <- graph(personId2) += "city" -> "Sydney"
        _ <- graph(personId1) ~> graph(personId2)

      } yield {
        whenReady(graph(personId1) hasRelation personId2)(_ shouldEqual true)
        whenReady(graph(personId2) hasRelation personId1)(_ shouldEqual false)
      }
    }
  }

  "API querying Value" should {
    "FIELD(X) where X is the name of an attribute defined on the node" in {

      import graph._

      for {
        personId1 <- graph += "Person"
        _ <- graph(personId1) += "gender" -> "Male"
      } yield {
        whenReady(graph(personId1) get Field("gender"))(_ shouldEqual StringValue("Male"))
      }
    }

    "BOOL(x) where X is scala.Boolean" in {

      import graph._

      for {
        personId1 <- graph += "Person"
        _ <- graph(personId1) += "married" -> false
      } yield {
        whenReady(graph(personId1) get Field("married"))(_ shouldEqual BoolValue(false))
      }
    }

    "NUMBER(X) where X is scala.Double" in {

      import graph._

      for {
        personId1 <- graph += "Person"
        _ <- graph(personId1) += "worth" -> 98888.333
      } yield {
        whenReady(graph(personId1) get Field("worth"))(_ shouldEqual NumberValue(98888.333))
      }
    }

    "STR(X) where X is scala.String" in {

      import graph._

      for {
        personId1 <- graph += "Person"
        _ <- graph(personId1) += "vehicle" -> "Mazda"
      } yield {
        whenReady(graph(personId1) get Field("vehicle"))(_ shouldEqual StringValue("Mazda"))
      }
    }

    "LST(X) where X is Values" in {

      import graph._

      for {
        personId1 <- graph += "Person"
        _ <- graph(personId1) += "stocks" -> Seq("DAL", "TSLA")
        _ <- graph(personId1) += "currencies" -> Seq("USD", "AUD")
      } yield {
        whenReady(graph(personId1) get Field("stocks"))(
          _ shouldEqual ListValue(
            Set(
              StringValue("DAL"),
              StringValue("TSLA")
            )))

        whenReady(graph(personId1) get Field("currencies"))(_ shouldEqual ListValue(Set(StringValue("USD"), StringValue("AUD"))))

      }

    }

  }

  "Operations" should {

    "All friends of a given person" in {

      import graph._

      for {
        person <- graph += "Person"
        friend <- graph += "Person"
        _ <- graph(person) ~> graph(friend)
      } yield {
        whenReady(graph(person) relationIds)(_ shouldEqual Set(friend))
      }

    }

    "List all employed persons" in {

      import graph._

      for {
        person1 <- graph += "Person"
        person2 <- graph += "Person"
        business <- graph += "Business" within ("name" -> "WestBank")
        _ <- graph(person1) ~> "EmployedBy" ~> graph(business)
        _ <- graph(person2) ~> "EmployedBy" ~> graph(business)
      } yield {
        whenReady(graph find And(HasNodeType("Person"), HasRelationType("EmployedBy"))) {
          _.nodes.map(_.nodeId) shouldEqual Set(person1, person2)
        }
      }

    }

    "List all persons employed by a business located in the area X" in {

      import graph._

      for {
        personId1 <- graph += "Person" within ("name" -> "Jessica")
        _ <- graph += "Person"
        personId3 <- graph += "Person" within ("name" -> "Donny")
        _ <- graph += "Person"
        businessId1 <- graph += "Business" within("name" -> "Google", "location" -> "US")
        _ <- graph(businessId1) ~> "Employee" ~> graph(personId1)
        _ <- graph(businessId1) ~> "Employee" ~> graph(personId3)

      } yield {
        whenReady(graph find And(Eq(Field("location"), StringValue("US")), HasNodeType("Business"))) { nodes =>
          val employeeIds: Set[NodeId] =
            nodes.nodes.flatMap(_.relation("Employee"))

          employeeIds shouldEqual Set(personId1, personId3)

          whenReady(graph findIds employeeIds)(
            _.nodes.map(_.get(Field("name"))) shouldEqual Set(StringValue("Jessica"), StringValue("Donny"))
          )
        }
      }
    }

  }

  override protected def beforeAll(): Unit = {

    Main.startCassandraDatabase()
    Main.createTables(system)


  }
  
}
