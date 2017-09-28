addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.0-RC12")
addSbtPlugin("ch.epfl.lamp" % "sbt-dotty" % "0.1.5")
addSbtPlugin("de.heikoseeberger" % "sbt-header" % "3.0.1")
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.5.1")
addSbtPlugin("org.scoverage" % "sbt-coveralls" % "1.1.0")
addSbtPlugin("com.codacy" % "sbt-codacy-coverage" % "1.3.10")
addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "0.9.3")
addSbtPlugin("org.foundweekends" % "sbt-bintray" % "0.5.1")
addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.1.0")
addSbtPlugin("com.sksamuel.scapegoat" %% "sbt-scapegoat" % "1.0.5")

dependencyOverrides ++= Set(
  "com.fasterxml.jackson.core" % "jackson-core" % "2.9.1",
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.9.1"
)
