val connIdVersion = "1.4.2.35"

lazy val root = (project in file("."))
  .enablePlugins(GitVersioning, AutomateHeaderPlugin)
  .settings(
    organization := "eu.arthepsy.midpoint",
    name := "connid-http",
    description := "Library for http-based ConnId connectors",

    scalaVersion := "2.12.4",
    crossSbtVersions := Seq("1.0.4", "0.13.16"),
    crossScalaVersions := Seq(scalaVersion.value, "2.11.12", "2.13.0-M2", "0.5.0-RC1"),

    resolvers ++= Seq(
      "Evolveum releases" at "https://nexus.evolveum.com/nexus/content/repositories/releases/",
      "Evolveum snapshots" at "https://nexus.evolveum.com/nexus/content/repositories/snapshots/"
    ),
    libraryDependencies ++= Seq(
      "net.tirasa.connid" % "connector-framework" % connIdVersion % Provided.name,
      "net.tirasa.connid" % "connector-framework-contract" % connIdVersion % Provided.name
    ),

    libraryDependencies ++= Seq(
      "org.apache.httpcomponents" % "httpclient" % "4.5.4"
    ),

    fork in Test := true,
    libraryDependencies ++= (scalaVersion.value match {
      case "0.5.0-RC1" => Seq("org.scalatest" % "scalatest_2.12" % "3.0.4" % "test")
      case _ => Seq("org.scalatest" %% "scalatest" % "3.0.4" % "test")
    }),
    libraryDependencies ++= Seq(
      "org.mockito" % "mockito-core" % "2.13.0" % "test",
      "com.github.tomakehurst" % "wiremock" % "2.12.0" % "test"
    ),

    git.baseVersion := "1.0",
    git.useGitDescribe := true,
    git.uncommittedSignifier := None,

    bintrayOrganization := None,
    bintrayRepository := "maven",
    publishMavenStyle := true,
    publishArtifact in Test := true,

    PgpKeys.useGpg in Global := true,
    PgpKeys.gpgCommand in Global := "gpg2",

    licenses := Seq("MIT" -> url("https://opensource.org/licenses/MIT")),
    headerLicense := Some(HeaderLicense.Custom(IO.read(baseDirectory.value / "LICENSE.md")))
  )