import sbtorgpolicies.runnable.syntax._

lazy val root = (project in file("."))
  .settings(moduleName := "root")
  .settings(name := "freestyle")
  .settings(noPublishSettings: _*)
  .aggregate(allModules: _*)

lazy val core = module("core")
  .jsSettings(sharedJsSettings: _*)
  .settings(libraryDependencies ++= Seq(%("scala-reflect", scalaVersion.value)))
  .settings(
    wartremoverWarnings in(Test, compile) := Warts.unsafe,
    wartremoverWarnings in(Test, compile) ++= Seq(
      Wart.FinalCaseClass,
      Wart.ExplicitImplicitTypes),
    wartremoverWarnings in(Test, compile) -= Wart.NonUnitStatements
  )
  .crossDepSettings(
    commonDeps ++ Seq(
      %("cats-free"),
      %("iota-core"),
      %("simulacrum"),
      %("shapeless") % "test",
      %("cats-laws") % "test"
    ): _*
  )

lazy val coreJVM = core.jvm
lazy val coreJS = core.js

lazy val tagless = module("tagless")
  .dependsOn(core)
  .jsSettings(sharedJsSettings: _*)
  .crossDepSettings(commonDeps: _*)
  .settings(
    libraryDependencies += "com.kailuowang" %%% "mainecoon-core" % "0.4.0"
  )

lazy val taglessJVM = tagless.jvm
lazy val taglessJS = tagless.js

lazy val tests = jvmModule("tests")
  .dependsOn(coreJVM % "compile->compile;test->test")
  .settings(noPublishSettings: _*)
  .settings(
    libraryDependencies ++= commonDeps ++ Seq(
      %("scala-reflect", scalaVersion.value),
      %%("pcplod") % "test",
      %%("monix-eval") % "test"
    ),
    fork in Test := true,
    javaOptions in Test ++= {
      val excludedScalacOptions: List[String] = List("-Yliteral-types", "-Ypartial-unification")
      val options = (scalacOptions in Test).value.distinct
        .filterNot(excludedScalacOptions.contains)
        .mkString(",")
      val cp = (fullClasspath in Test).value.map(_.data).filter(_.exists()).distinct.mkString(",")
      Seq(
        s"""-Dpcplod.settings=$options""",
        s"""-Dpcplod.classpath=$cp"""
      )
    }
  )

lazy val bench = jvmModule("bench")
  .dependsOn(jvmFreestyleDeps: _*)
  .settings(
    name := "bench",
    description := "freestyle benchmark"
  )
  .enablePlugins(JmhPlugin)
  .configs(Codegen)
  .settings(inConfig(Codegen)(Defaults.configSettings))
  .settings(classpathConfiguration in Codegen := Compile)
  .settings(noPublishSettings)
  .settings(libraryDependencies ++= Seq(%%("cats-free"), %%("scalacheck")))
  .settings(inConfig(Compile)(
    sourceGenerators += Def.task {
      val path = (sourceManaged in(Compile, compile)).value / "bench.scala"
      (runner in(Codegen, run)).value.run(
        "freestyle.bench.BenchBoiler",
        Attributed.data((fullClasspath in Codegen).value),
        path.toString :: Nil,
        streams.value.log)
      path :: Nil
    }
  ))

lazy val Codegen = sbt.config("codegen").hide

lazy val effects = module("effects")
  .dependsOn(core)
  .jsSettings(sharedJsSettings: _*)
  .crossDepSettings(commonDeps ++ Seq(
    %("cats-mtl-core")
  ): _*)

lazy val effectsJVM = effects.jvm
lazy val effectsJS = effects.js

lazy val async = module("async", subFolder = Some("async"))
  .dependsOn(core)
  .jsSettings(sharedJsSettings: _*)
  .crossDepSettings(commonDeps: _*)

lazy val asyncJVM = async.jvm
lazy val asyncJS = async.js

lazy val asyncCatsEffect = module("async-cats-effect", subFolder = Some("async"))
  .dependsOn(core, async)
  .jsSettings(sharedJsSettings: _*)
  .crossDepSettings(commonDeps ++ Seq(
    %("cats-effect")
  ): _*)

lazy val asyncCatsEffectJVM = asyncCatsEffect.jvm
lazy val asyncCatsEffectJS = asyncCatsEffect.js

lazy val asyncGuava = jvmModule("async-guava", subFolder = Some("async"))
  .dependsOn(coreJVM, asyncJVM)
  .settings(libraryDependencies ++= commonDeps ++ Seq(
    "com.google.guava" % "guava" % "22.0"
  ))

lazy val cache = module("cache")
  .dependsOn(core)
  .jsSettings(sharedJsSettings: _*)
  .crossDepSettings(commonDeps: _*)

lazy val cacheJVM = cache.jvm
lazy val cacheJS = cache.js

lazy val config = jvmModule("config")
  .dependsOn(coreJVM)
  .settings(
    fixResources := {
      val testConf = (resourceDirectory in Test).value / "application.conf"
      val targetFile = (classDirectory in(coreJVM, Compile)).value / "application.conf"
      if (testConf.exists) {
        IO.copyFile(
          testConf,
          targetFile
        )
      }
    },
    compile in Test := ((compile in Test) dependsOn fixResources).value
  )
  .settings(
    libraryDependencies ++= Seq(
      %("config", "1.2.1"),
      %%("classy-config-typesafe"),
      %%("classy-core")
    ) ++ commonDeps
  )

lazy val logging = module("logging")
  .dependsOn(core)
  .jvmSettings(
    libraryDependencies += %%("journal-core")
  )
  .jsSettings(
    libraryDependencies += %%%("slogging")
  )
  .jsSettings(sharedJsSettings: _*)
  .crossDepSettings(commonDeps ++ Seq("com.lihaoyi" %% "sourcecode" % "0.1.3"): _*)

lazy val loggingJVM = logging.jvm
lazy val loggingJS = logging.js


//////////////////////
//// INTEGRATIONS ////
//////////////////////


lazy val monix = module("monix", full = false, subFolder = Some("integrations"))
  .dependsOn(core)
  .jsSettings(sharedJsSettings: _*)
  .crossDepSettings(commonDeps ++
    Seq(%("monix-eval")): _*)

lazy val monixJVM = monix.jvm
lazy val monixJS = monix.js

lazy val cacheRedis = jvmModule("cache-redis", subFolder = Some("integrations"))
  .dependsOn(coreJVM, cacheJVM)
  .settings(
    resolvers += "Sonatype OSS Releases" at "https://oss.sonatype.org/content/repositories/releases/",
    resolvers += Resolver.mavenLocal,
    libraryDependencies ++= Seq(
      %%("rediscala"),
      %%("akka-actor") % "test",
      %("embedded-redis") % "test"
    ) ++ commonDeps
  )

lazy val doobie = jvmModule("doobie", subFolder = Some("integrations"))
  .dependsOn(coreJVM)
  .settings(
    libraryDependencies ++= Seq(
      %%("doobie-core"),
      %%("doobie-h2") % "test"
    ) ++ commonDeps
  )

lazy val slick = jvmModule("slick", subFolder = Some("integrations"))
  .dependsOn(coreJVM, asyncJVM)
  .settings(
    libraryDependencies ++= Seq(
      %%("slick"),
      %("h2") % "test"
    ) ++ commonDeps
  )

lazy val twitterUtil = jvmModule("twitter-util", subFolder = Some("integrations"))
  .dependsOn(coreJVM)
  .settings(
    libraryDependencies ++= Seq(%%("catbird-util")) ++ commonDeps
  )

lazy val fetch = module("fetch", subFolder = Some("integrations"))
  .dependsOn(core)
  .jsSettings(sharedJsSettings: _*)
  .crossDepSettings(
    commonDeps ++ Seq(%("fetch")): _*
  )

lazy val fetchJVM = fetch.jvm
lazy val fetchJS = fetch.js

// lazy val fs2 = module("fs2", subFolder = Some("integrations"))
//  .dependsOn(core)
//   .jsSettings(sharedJsSettings: _*)
//   .crossDepSettings(
//     commonDeps ++ Seq(
//       %("fs2-core")
//     ): _*
//   )

// lazy val fs2JVM = fs2.jvm
// lazy val fs2JS  = fs2.js

lazy val httpHttp4s = jvmModule("http4s", subFolder = Some("integrations/http"))
  .dependsOn(coreJVM)
  .settings(
    libraryDependencies ++= Seq(
      %%("http4s-core"),
      %%("http4s-dsl") % "test"
    ) ++ commonDeps
  )

lazy val httpFinch = jvmModule("finch", subFolder = Some("integrations/http"))
  .dependsOn(coreJVM)
  .settings(
    libraryDependencies ++= Seq(%%("finch-core")) ++ commonDeps
  )

lazy val httpAkka = jvmModule("akka", subFolder = Some("integrations/http"))
  .dependsOn(coreJVM)
  .settings(
    libraryDependencies ++= Seq(
      %%("akka-http"),
      %%("akka-http-testkit") % "test"
    ) ++ commonDeps
  )

lazy val httpPlay = jvmModule("play", subFolder = Some("integrations/http"))
  .dependsOn(coreJVM)
  .settings(
    concurrentRestrictions in Global := Seq(Tags.limitAll(1)),
    libraryDependencies ++= Seq(
      %%("play") % "test",
      %%("play-test") % "test"
    ) ++ commonDeps
  )


/////////////////////
//// ALL MODULES ////
/////////////////////


lazy val jvmModules: Seq[ProjectReference] = Seq(
  coreJVM,
  taglessJVM,
  effectsJVM,
  asyncJVM,
  asyncCatsEffectJVM,
  asyncGuava,
  cacheJVM,
  config,
  loggingJVM,
  //Integrations:
  monixJVM,
  cacheRedis,
  doobie,
  slick,
  twitterUtil,
  fetchJVM,
  // fs2JVM,
  httpHttp4s,
  httpFinch,
  httpAkka,
  httpPlay
  // ,tests
)

lazy val jsModules: Seq[ProjectReference] = Seq(
  coreJS,
  taglessJS,
  effectsJS,
  asyncJS,
  asyncCatsEffectJS,
  cacheJS,
  loggingJS,
  //Integrations:
  monixJS,
  fetchJS
  //, fs2JS
)

lazy val allModules: Seq[ProjectReference] = jvmModules ++ jsModules

lazy val jvmFreestyleDeps: Seq[ClasspathDependency] =
  jvmModules.map(ClasspathDependency(_, None))

addCommandAlias("validateJVM", (toCompileTestList(jvmModules) ++ List("project root")).asCmd)
addCommandAlias("validateJS", (toCompileTestList(jsModules) ++ List("project root")).asCmd)
addCommandAlias(
  "validate",
  ";clean;compile;coverage;validateJVM;coverageReport;coverageAggregate;coverageOff")


///////////////
//// DOCS ////
///////////////

lazy val docs = (project in file("docs"))
  .dependsOn(jvmFreestyleDeps: _*)
  .settings(moduleName := "frees-docs")
  .settings(micrositeSettings: _*)
  .settings(noPublishSettings: _*)
  .settings(
    addCompilerPlugin(%%("scalameta-paradise") cross CrossVersion.full),
    libraryDependencies += %%("scalameta", "1.8.0"),
    scalacOptions += "-Xplugin-require:macroparadise"
  )
  .settings(
    resolvers ++= Seq(
      Resolver.mavenLocal,
      Resolver.bintrayRepo("kailuowang", "maven")
    ),
    libraryDependencies ++= Seq(
      %%("doobie-h2"),
      %%("http4s-dsl"),
      %%("play"),
      %("h2") % "test"
    )
  )
  .settings(
    scalacOptions in Tut ~= (_ filterNot Set("-Ywarn-unused-import", "-Xlint").contains)
  )
  .enablePlugins(MicrositesPlugin)

pgpPassphrase := Some(getEnvVar("PGP_PASSPHRASE").getOrElse("").toCharArray)
pgpPublicRing := file(s"$gpgFolder/pubring.gpg")
pgpSecretRing := file(s"$gpgFolder/secring.gpg")