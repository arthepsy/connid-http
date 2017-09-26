scapegoatVersion in ThisBuild := "1.3.2"

val scapegoatAvailable = Def.setting {
  val versions = "sbt " + sbtVersion.value +
    ", cross-sbt " + (sbtVersion in pluginCrossBuild).value +
    ", scala " + scalaVersion.value +
    ", scala-binary " + scalaBinaryVersion.value
  CrossVersion.partialVersion(scalaBinaryVersion.value) match {
    case Some((2, 11)) => (true, versions)
    case Some((2, 12)) => (true, versions)
    case _             => (false, versions)
  }
}

libraryDependencies --= libraryDependencies.value.filter(_.name.startsWith("scalac-scapegoat-plugin"))

libraryDependencies ++= {
  if (scapegoatAvailable.value._1) {
    Seq(
      "com.sksamuel.scapegoat" % ("scalac-scapegoat-plugin" + "_" + scalaBinaryVersion.value) % scapegoatVersion.value % "compile"
    )
  } else {
    Seq.empty
  }
}

scapegoat := Def.taskDyn {
  val default = scapegoat.taskValue
  if (scapegoatAvailable.value._1) Def.task(default.value)
  else
    Def.task {
      val log = streams.value.log
      log.warn(s"scapegoat not supported (${scapegoatAvailable.value._2})")
    }
}.value
