import sbt._
import Keys._
import com.typesafe.sbt.pgp.PgpKeys._
import Tools.onVersion
import sbtrelease.ReleasePlugin
import sbtrelease.ReleasePlugin.autoImport._
import com.typesafe.tools.mima.plugin.MimaPlugin._
import com.typesafe.tools.mima.plugin.MimaKeys._
import org.scalajs.sbtplugin.cross.{ CrossProject, CrossType }

object build {
  type Sett = Def.Setting[_]

  val base = ScalaSettings.all ++ Seq[Sett](
      organization := "io.argonaut"
  )

  val scalazVersion              = "7.2.8"
  val paradiseVersion            = "2.1.0"
  val monocleVersion             = "1.4.0-M2"
  val catsVersion                = "0.8.1"
  val scalacheckVersion          = "1.13.4"

  def reflect(v: String)         =
                                    Seq("org.scala-lang" % "scala-reflect"  % v) ++
           (if (v.contains("2.10")) Seq("org.scalamacros" %% "quasiquotes" % paradiseVersion) else Seq())

  private[this] val tagName = Def.setting {
    s"v${if (releaseUseGlobalVersion.value) (version in ThisBuild).value else version.value}"
  }

  private[this] val tagOrHash = Def.setting {
    if(isSnapshot.value) {
      sys.process.Process("git rev-parse HEAD").lines_!.head
    } else {
      tagName.value
    }
  }

  val commonSettings = base ++
    ReplSettings.all ++
    ReleasePlugin.projectSettings ++
    PublishSettings.all ++
    Seq(addCompilerPlugin("org.scalamacros" % "paradise" % paradiseVersion cross CrossVersion.full)) ++
    Seq[Sett](
      scalacOptions += "-language:_"
    , scalacOptions in (Compile, doc) ++= {
        val base = (baseDirectory in LocalRootProject).value.getAbsolutePath
        Seq("-sourcepath", base, "-doc-source-url", "https://github.com/argonaut-io/argonaut/tree/" + tagOrHash.value + "€{FILE_PATH}.scala")
      }
    , releaseTagName := tagName.value
    , resolvers += Resolver.sonatypeRepo("releases")
    , resolvers += Resolver.sonatypeRepo("snapshots")
    , autoScalaLibrary := false
    , libraryDependencies ++= reflect(scalaVersion.value)
    // no mima until 6.2.0 release.
    , previousArtifact := None
    /*
    , binaryIssueFilters ++= {
      import com.typesafe.tools.mima.core._
      import com.typesafe.tools.mima.core.ProblemFilters._
      /* adding functions to sealed traits is binary incompatible from java, but ok for scala, so ignoring */
      Seq(
      ) map exclude[MissingMethodProblem]
    }
    */
  )

  val jvmSettings = Seq[Sett](
    libraryDependencies ++= Seq(
      "org.scalacheck"               %%  "scalacheck"                % scalacheckVersion        % "test"
    , "org.specs2"                   %%  "specs2-scalacheck"         % "3.8.6"                  % "test"
    )
  )

  def argonautCrossProject(name: String) = {
    CrossProject(name, file(name), CrossType.Full)
      .settings(commonSettings)
      .jvmSettings(jvmSettings)
      .jsSettings(
        scalacOptions += {
          val a = (baseDirectory in LocalRootProject).value.toURI.toString
          val g = "https://raw.githubusercontent.com/argonaut-io/argonaut/" + tagOrHash.value
          s"-P:scalajs:mapSourceURI:$a->$g/"
        }
      )
  }
}
