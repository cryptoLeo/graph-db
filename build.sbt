val AkkaVersion                     = "2.6.5"
val AkkaPersistenceCassandraVersion = "1.0.0-RC2"
val AkkaHttpVersion                 = "10.1.10"
val ScalaMockVersion                = "4.4.0"
val AkkaClusterBootstrapVersion = "1.0.5"


lazy val `graph-db` = project
  .in(file("."))
  .enablePlugins(
    JavaAppPackaging,
    DockerPlugin,
    AshScriptPlugin
  )
  .settings(
    organization := "com.examples",
    version := "1.8",
    scalaVersion := "2.13.2",
    scalacOptions in Compile ++= Seq("-deprecation", "-feature", "-unchecked", "-Xlog-reflective-calls", "-Xlint"),
    javacOptions in Compile ++= Seq("-Xlint:unchecked", "-Xlint:deprecation"),
    libraryDependencies ++= Seq(
      "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % AkkaClusterBootstrapVersion,
      "com.lightbend.akka.discovery" %% "akka-discovery-kubernetes-api" % AkkaClusterBootstrapVersion,
      "com.lightbend.akka.management" %% "akka-management-cluster-http" % AkkaClusterBootstrapVersion,
      "com.typesafe.akka" %% "akka-discovery" % AkkaVersion,
      "com.typesafe.akka" %% "akka-cluster-sharding-typed" % AkkaVersion,
      "com.typesafe.akka" %% "akka-persistence-typed" % AkkaVersion,
      "com.typesafe.akka" %% "akka-persistence-query" % AkkaVersion,
      "com.typesafe.akka" %% "akka-serialization-jackson" % AkkaVersion,
      "com.typesafe.akka" %% "akka-persistence-cassandra" % AkkaPersistenceCassandraVersion,
      "com.typesafe.akka" %% "akka-persistence-cassandra-launcher" % AkkaPersistenceCassandraVersion,
      "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
      "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
      "ch.qos.logback" % "logback-classic" % "1.2.3",
       "com.iheart" %% "ficus" % "1.4.7",

     "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
      "org.scalatest" %% "scalatest" % "3.1.1" % Test,
      "org.scalamock"     %% "scalamock"  % ScalaMockVersion % Test,

      "commons-io" % "commons-io" % "2.4" % Test),
    fork in run := false,
    Global / cancelable := false, // ctrl-c
    mainClass in (Compile, run) := Some("com.example.graphdb.Main"),
    // disable parallel tests
    parallelExecution in Test := false,
    // show full stack traces and test case durations
    testOptions in Test += Tests.Argument("-oDF"),
    logBuffered in Test := false
  )