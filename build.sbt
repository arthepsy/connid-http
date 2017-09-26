val connIdVersion = "1.4.2.35"

lazy val root = (project in file("."))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    organization := "eu.arthepsy.midpoint",
    name := "connid-http",
    description := "Library for http-based ConnId connectors",
    version := "1.0-SNAPSHOT",

    scalaVersion := "2.12.3",
    crossScalaVersions := Seq(scalaVersion.value, "2.11.11", "2.10.6", "0.3.0-RC2"),

    resolvers ++= Seq(
      "Evolveum releases" at "https://nexus.evolveum.com/nexus/content/repositories/releases/",
      "Evolveum snapshots" at "https://nexus.evolveum.com/nexus/content/repositories/snapshots/"
    ),
    libraryDependencies ++= Seq(
      "net.tirasa.connid" % "connector-framework" % connIdVersion % Provided.name,
      "net.tirasa.connid" % "connector-framework-contract" % connIdVersion % Provided.name
    ),

    libraryDependencies ++= Seq(
      "org.json" % "json" % "20170516",
      "org.apache.httpcomponents" % "httpclient" % "4.5.3"
    ),

    fork in Test := true,
    libraryDependencies ++= (scalaVersion.value match {
      case "0.3.0-RC2" => Seq("org.scalatest" % "scalatest_2.11" % "3.0.3" % "test")
      case _ => Seq("org.scalatest" %% "scalatest" % "3.0.3" % "test")
    }),
    libraryDependencies ++= Seq(
      "org.mockito" % "mockito-core" % "2.10.0" % "test",
      "com.github.tomakehurst" % "wiremock" % "2.8.0" % "test"
    ),

    licenses := Seq("MIT" -> url("https://opensource.org/licenses/MIT")),
    headerLicense := Some(HeaderLicense.Custom(IO.read(baseDirectory.value / "LICENSE.md")))
  )