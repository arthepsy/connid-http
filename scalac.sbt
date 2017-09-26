scalacOptions ++= {
  val vn = VersionNumber(scalaVersion.value)

  def cmp[A](vn: VersionNumber, direction: Int, xs: Seq[Long], ret: Seq[A]) =
    if (vn.numbers.zipWithIndex.forall({
      case (n, i) => n.compare(xs.lift(i).getOrElse(n)) * direction >= 0
    })) ret else Seq.empty

  Seq(
    "-deprecation",                          // Emit warning and location for usages of deprecated APIs.
    "-encoding", "utf-8",                    // Specify character encoding used by source files.
    "-feature",                              // Emit warning and location for usages of features that should be imported explicitly.
    "-unchecked",                            // Enable additional warnings where generated code depends on assumptions.
    "-Xfatal-warnings"                       // Fail the compilation if there are any warnings.
  ) ++ cmp(vn, -1, Seq(0, 2, 0), Seq(
    "-explain",                              // Explain errors in more detail.
    "-explain-implicits",                    // Explain implicit search errors in more detail.
    "-explain-types"                         // Explain type errors in more detail.
  )) ++ cmp(vn, 1, Seq(2, 10, 0), Seq(
    "-explaintypes",                         // Explain type errors in more detail.
    "-language:existentials",                // Existential types (besides wildcard types) can be written and inferred
    "-language:experimental.macros",         // Allow macro definition (besides implementation and application)
    "-language:higherKinds",                 // Allow higher-kinded types
    "-language:implicitConversions",         // Allow definition of implicit functions called views
    "-Xcheckinit",                           // Wrap field accessors to throw an exception on uninitialized access.
    "-Xfuture",                              // Turn on future language features.
    "-Yno-adapted-args",                     // Do not adapt an argument list (either by inserting () or creating a tuple) to match the receiver.
    "-Ywarn-dead-code",                      // Warn when dead code is identified.
    "-Ywarn-inaccessible",                   // Warn about inaccessible types in method signatures.
    "-Ywarn-nullary-override",               // Warn when non-nullary `def f()' overrides nullary `def f'.
    "-Ywarn-nullary-unit",                   // Warn when nullary methods return Unit.
    "-Ywarn-numeric-widen",                  // Warn when numerics are widened.
    "-Ywarn-value-discard"                   // Warn when non-Unit expression results are unused.
  )) ++ cmp(vn, 1, Seq(2, 11, 0), Seq(
    "-Ywarn-infer-any"                       // Warn when a type argument is inferred to be `Any`.
  )) ++ cmp(vn, 1, Seq(2, 11, 2), Seq(
    "-Xlint:adapted-args",                   // Warn if an argument list is modified to match the receiver.
    "-Xlint:by-name-right-associative",      // By-name parameter of right associative operator.
    "-Xlint:delayedinit-select",             // Selecting member of DelayedInit.
    "-Xlint:doc-detached",                   // A Scaladoc comment appears to be detached from its element.
    "-Xlint:inaccessible",                   // Warn about inaccessible types in method signatures.
    "-Xlint:infer-any",                      // Warn when a type argument is inferred to be `Any`.
    "-Xlint:missing-interpolator",           // A string literal appears to be missing an interpolator id.
    "-Xlint:nullary-override",               // Warn when non-nullary `def f()' overrides nullary `def f'.
    "-Xlint:nullary-unit",                   // Warn when nullary methods return Unit.
    "-Xlint:option-implicit",                // Option.apply used implicit view.
    "-Xlint:package-object-classes",         // Class or object defined in package object.
    "-Xlint:poly-implicit-overload",         // Parameterized overloaded implicit methods are not visible as view bounds.
    "-Xlint:private-shadow",                 // A private field (or class parameter) shadows a superclass field.
    "-Xlint:unsound-match"                   // Pattern match may not be typesafe.
  )) ++ cmp(vn, 1, Seq(2, 11, 3), Seq(
    "-target:jvm-1.8",                       // Target platform for object files.
    "-Xlint:type-parameter-shadow"           // A local type parameter shadows a type already in scope.
  )) ++ cmp(vn, 1, Seq(2, 11, 6), Seq(
    "-Xlint:stars-align"                     // Pattern sequence wildcard must align with sequence component.
  )) ++ cmp(vn, 1, Seq(2, 11, 9), Seq(
    "-Ypartial-unification"                  // Enable partial unification in type constructor inference
  )) ++ cmp(vn, 1, Seq(2, 12, 0), Seq(
    "-Xlint:constant"                        // Evaluation of a constant arithmetic expression results in an error.
  )) ++ cmp(vn, 1, Seq(2, 12, 2), Seq(
    "-Ywarn-extra-implicit",                 // Warn when more than one implicit parameter section is defined.
    "-Ywarn-unused:patvars",                 // Warn if a variable bound in a pattern is unused.
    "-Ywarn-unused:privates",                // Warn if a private member is unused.
    "-Ywarn-unused:params",                  // Warn if a value parameter is unused.
    "-Ywarn-unused:implicits",               // Warn if an implicit parameter is unused.
    "-Ywarn-unused:imports",                 // Warn if an import selector is not referenced.
    "-Ywarn-unused:locals"                   // Warn if a local definition is unused.
  ))
}

scalacOptions in (Compile, console) ~= (_.filterNot(
  Set(
    "-Ywarn-unused:imports",
    "-Xfatal-warnings"
  )))
