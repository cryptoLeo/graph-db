package com.example.graphdb.graph

import java.util.UUID

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import com.example.graphdb.graph.GraphContext.{NumberValue, StringValue}
import com.example.graphdb.graph.GraphNodeEntity._
import org.scalatest.wordspec.AnyWordSpecLike

class GraphNodeSpec
    extends ScalaTestWithActorTestKit(s"""
      akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
      akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"
      akka.persistence.snapshot-store.local.dir = "target/snapshot-${UUID
      .randomUUID()
      .toString}"
    """)
    with AnyWordSpecLike {

  "Entity" should {

    "create node" in new Fixture {

      val person = testKit.spawn(GraphNodeEntity(nodeId, Set()))

      val probe = testKit.createTestProbe[Reply]

      val fields = Map("age" -> NumberValue(10))

      person ! Create(nodeId, "Person", fields, probe.ref)

      probe.expectMessage(SuccessReply(GraphNodeEntity.GraphNodeState(nodeId, "Person", fields)))
    }

    "create node error: node is already created" in new Fixture {

      val person = testKit.spawn(GraphNodeEntity(nodeId, Set()))

      val probe = testKit.createTestProbe[Reply]

      val fields = Map("age" -> NumberValue(10))

      person ! Create(nodeId, "Person", fields, ignoreRef)

      person ! Create(nodeId, "Person", fields, probe.ref)

      probe.expectMessage(ReplyError("Node is already created!"))
    }

    "add fields to node" in new Fixture {

      val person = testKit.spawn(GraphNodeEntity(nodeId, Set()))

      val probe = testKit.createTestProbe[Reply]

      val fields = Map("age" -> NumberValue(10))

      val newFields = Map("name" -> StringValue("Jessica"))

      person ! Create(nodeId, "Person", fields, ignoreRef)

      person ! AddFields(newFields, probe.ref)

      probe.expectMessage(
        SuccessReply(
          GraphNodeEntity.GraphNodeState(nodeId, "Person", Map("age" -> NumberValue(10), "name" -> StringValue("Jessica")))
        )
      )

    }

    "remove field from node" in new Fixture {

      val person = testKit.spawn(GraphNodeEntity(nodeId, Set()))

      val probe = testKit.createTestProbe[Reply]

      val fields = Map("age" -> NumberValue(10), "name" -> StringValue("Jessica"))

      person ! Create(nodeId, "Person", fields, ignoreRef)

      person ! RemoveField("name", probe.ref)

      probe.expectMessage(
        SuccessReply(GraphNodeEntity.GraphNodeState(nodeId, "Person", Map("age" -> NumberValue(10))))
      )

    }
  }

  trait Fixture {
    val ignoreRef = system.ignoreRef[Reply]


    val age1 = 12
    val age2 = 43

    val nodeId  = UUID.randomUUID().toString
    val nodeId2 = UUID.randomUUID().toString

    val friendId1 = UUID.randomUUID().toString
    val friendId2 =UUID.randomUUID().toString
    val friendId3 = UUID.randomUUID().toString

  }

}
