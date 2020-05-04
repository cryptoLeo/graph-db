package com.example.graphdb

import java.io.File

import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.cluster.typed.Cluster
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import akka.persistence.cassandra.testkit.CassandraLauncher
import akka.stream.alpakka.cassandra.scaladsl.CassandraSessionRegistry
import com.example.graphdb.graph.{GraphContext, GraphNodeEntity}
import com.example.graphdb.http.{GraphDbRoutes, GraphDbServer}
import com.example.graphdb.readside.GraphEventProcessorStream
import com.typesafe.config.{Config, ConfigFactory}
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._

import scala.concurrent.duration._
import scala.concurrent.{Await}

object Main {

  def main(args: Array[String]): Unit = {


    startNode()

  }


  def startNode(): Unit = {

    val maybeEnvStr = sys.env.get("CONF_ENV")
    println(s"Loading config from $maybeEnvStr")

    val config: Config = maybeEnvStr.fold(ConfigFactory.load()) { env =>
      ConfigFactory.load(s"application-$env").withFallback(ConfigFactory.load())
    }

    startCassandraDatabase();



    val system = ActorSystem[Nothing](Guardian(), "ClusterSystem", config)

    val cluster = Cluster(system)
    system.log.info("Started [" + system + "], cluster.selfAddress = " + cluster.selfMember.address + ")")

    import akka.actor.typed.scaladsl.adapter._



    if (Cluster(system).selfMember.hasRole("read-model"))
      createTables(system)

    val classicSystem = system.toClassic
    AkkaManagement.get(classicSystem).start
    ClusterBootstrap.get(classicSystem).start

    config.entrySet().forEach(nxt => system.log.info(s"$nxt"))

  }

  /***
   * Automatically Start the database for testing
   */
  def startCassandraDatabase(): Unit = {
    val databaseDirectory = new File("target/cassandra-db")
    CassandraLauncher.start(databaseDirectory, CassandraLauncher.DefaultTestConfigResource, clean = false, port = 9042)
  }


  def createTables(system: ActorSystem[_]): Unit = {
    val session =
      CassandraSessionRegistry(system).sessionFor("alpakka.cassandra")

    val keyspaceStmt =
      """
      CREATE KEYSPACE IF NOT EXISTS akka
      WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 1 }
      """

    val offsetTableStmt =
      """
      CREATE TABLE IF NOT EXISTS akka.offsetStore (
        eventProcessorId text,
        tag text,
        timeUuidOffset timeuuid,
        PRIMARY KEY (eventProcessorId, tag)
      )
      """

    val nodeTableStmt =
      """
      CREATE TABLE akka.nodes (
        type text,
        nodeId text,
        properties map<text, text>,
        relations map<text, text>,
        PRIMARY KEY (nodeId)
      )
      """

    Await.ready(session.executeDDL(keyspaceStmt), 30.seconds)
    Await.ready(session.executeDDL(offsetTableStmt), 30.seconds)
    Await.ready(session.executeDDL(nodeTableStmt), 30.seconds)

  }
}


object Guardian {
  def apply(): Behavior[Nothing] = {
    Behaviors.setup[Nothing] { context =>
      implicit val typedSystem: ActorSystem[Nothing] = context.system
      val eventProcessorSettings = EventProcessorSettings(typedSystem)
      implicit val sharding = ClusterSharding(typedSystem)
      val conf = ConfigFactory.load()

      val config = conf.as[GraphConfig]("GraphConfig")

      sharding.init(Entity(GraphNodeEntity.EntityKey) { entityContext =>
        val n = math.abs(
          entityContext.entityId.hashCode % eventProcessorSettings.parallelism
        )
        val eventProcessorTag = eventProcessorSettings.tagPrefix + "-" + n
        GraphNodeEntity(entityContext.entityId, Set(eventProcessorTag))
      }.withRole("write-model"))


      if (Cluster(typedSystem).selfMember.hasRole("read-model")) {
        EventProcessor.init(
          typedSystem,
          eventProcessorSettings,
          tag =>
            new GraphEventProcessorStream(
              typedSystem,
              typedSystem.executionContext,
              eventProcessorSettings.id,
              tag
            )
        )
      }
      implicit val graphContext: GraphContext = new GraphContext


      val routes = new GraphDbRoutes()
      new GraphDbServer(routes.route, config.http.port, context.system).start()

      Behaviors.empty
    }
  }

}