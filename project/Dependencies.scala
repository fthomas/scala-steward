import sbt._
import sbt.Keys._
import sbt.librarymanagement.syntax.ExclusionRule

object Dependencies {
  val attoCore = "org.tpolecat" %% "atto-core" % "0.9.5"
  val bcprovJdk15to18 = "org.bouncycastle" % "bcprov-jdk15to18" % "1.69"
  val betterFiles = "com.github.pathikrit" %% "better-files" % "3.9.1"
  val caseApp = "com.github.alexarchambault" %% "case-app" % "2.0.6"
  val catsEffect = "org.typelevel" %% "cats-effect" % "3.1.1"
  val catsCore = "org.typelevel" %% "cats-core" % "2.6.1"
  val catsLaws = "org.typelevel" %% "cats-laws" % catsCore.revision
  val catsParse = "org.typelevel" %% "cats-parse" % "0.3.4"
  val circeConfig = "io.circe" %% "circe-config" % "0.8.0"
  val circeGeneric = "io.circe" %% "circe-generic" % "0.14.1"
  val circeGenericExtras = "io.circe" %% "circe-generic-extras" % "0.13.0"
  val circeLiteral = "io.circe" %% "circe-literal" % circeGeneric.revision
  val circeParser = "io.circe" %% "circe-parser" % circeGeneric.revision
  val circeRefined = "io.circe" %% "circe-refined" % circeGeneric.revision
  val commonsIo = "commons-io" % "commons-io" % "2.11.0"
  val coursierCore = "io.get-coursier" %% "coursier" % "2.0.16"
  val cron4sCore = "com.github.alonsodomin.cron4s" %% "cron4s-core" % "0.6.1"
  val disciplineMunit = "org.typelevel" %% "discipline-munit" % "1.0.9"
  val fs2Core = "co.fs2" %% "fs2-core" % "3.0.6"
  val fs2Io = "co.fs2" %% "fs2-io" % fs2Core.revision
  val http4sCore = "org.http4s" %% "http4s-core" % "1.0.0-M23"
  val http4sCirce = "org.http4s" %% "http4s-circe" % http4sCore.revision
  val http4sClient = "org.http4s" %% "http4s-client" % http4sCore.revision
  val http4sDsl = "org.http4s" %% "http4s-dsl" % http4sCore.revision
  val http4sOkhttpClient = "org.http4s" %% "http4s-okhttp-client" % http4sCore.revision
  val log4catsSlf4j = "org.typelevel" %% "log4cats-slf4j" % "2.1.1"
  val logbackClassic = "ch.qos.logback" % "logback-classic" % "1.2.3"
  val jjwtApi = "io.jsonwebtoken" % "jjwt-api" % "0.11.2"
  val jjwtImpl = "io.jsonwebtoken" % "jjwt-impl" % jjwtApi.revision
  val jjwtJackson = "io.jsonwebtoken" % "jjwt-jackson" % jjwtApi.revision
  val millVersion = Def.setting(if (scalaBinaryVersion.value == "2.12") "0.6.3" else "0.9.8")
  val millScalalib = Def.setting("com.lihaoyi" %% "mill-scalalib" % millVersion.value)
  val monocleCore = "com.github.julien-truffaut" %% "monocle-core" % "2.1.0"
  val munit = "org.scalameta" %% "munit" % "0.7.27"
  val munitCatsEffect = "org.typelevel" %% "munit-cats-effect-3" % "1.0.5"
  val munitScalacheck = "org.scalameta" %% "munit-scalacheck" % munit.revision
  val refined = "eu.timepit" %% "refined" % "0.9.24"
  val refinedScalacheck = "eu.timepit" %% "refined-scalacheck" % refined.revision
  val scalacacheCaffeine = "com.github.cb372" %% "scalacache-caffeine" % "1.0.0-M2"
  val scalacheck = "org.scalacheck" %% "scalacheck" % "1.15.4"
}
