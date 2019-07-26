name := """bpmn-processor-play"""
organization := "org.hiroinamine"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala, BuildInfoPlugin)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoOptions += BuildInfoOption.ToJson,
    buildInfoPackage := "org.hiroinamine"
  )


scalaVersion := "2.13.0"

libraryDependencies ++= Seq(
  // Play
  guice,
  jdbc,
  caffeine,
  // Test
  "org.scalatestplus.play" %% "scalatestplus-play" % "4.0.3" % Test,
  // Json4s
  "org.json4s" %% "json4s-native" % "3.6.7",
  "org.json4s" %% "json4s-ext" % "3.6.7",
  // Camunda Workflow
  "org.camunda.bpm" % "camunda-bom" % "7.11.0",
  "org.camunda.bpm" % "camunda-engine" % "7.11.0",
  "org.camunda.bpm" % "camunda-engine-plugin-connect" % "7.11.0",
  "org.camunda.bpm" % "camunda-engine-plugin-spin" % "7.11.0",
  "org.camunda.connect" % "camunda-connect-connectors-all" % "1.1.4",
  "org.camunda.template-engines" % "camunda-template-engines-freemarker" % "1.1.0",
  "org.camunda.spin" % "camunda-spin-dataformat-json-jackson" % "1.6.7",
  // UUID
  "com.fasterxml.uuid" % "java-uuid-generator" % "3.2.0",
  // Database
  // "postgresql" % "postgresql" % "9.1-901-1.jdbc4"
  "com.h2database" % "h2" % "1.4.199"
)

// Adds additional packages into Twirl
//TwirlKeys.templateImports += "org.hiroinamine.controllers._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "org.hiroinamine.binders._"
