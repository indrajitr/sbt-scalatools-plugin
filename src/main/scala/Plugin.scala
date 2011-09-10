/*
 * Copyright 2011 Indrajit Raychaudhuri
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.scala_tools.sbt

import java.io.{File, IOException}
import sbt._
import Keys._


/**
  * SBT Plugin that can be used by projects intending to publish libraries to scala-tools.org repository
  * via the Nexus instance at http://nexus.scala-tools.org.
  *
  * @author Indrajit Raychaudhuri
  */
object ScalaToolsPlugin extends Plugin {

  lazy val ScalaTools = config("scala-tools") hide

  lazy val mavenSettings = SettingKey[File]("maven-settings", "Maven settings.xml file.")

  lazy val nexusRealm        = SettingKey[String]("nexus-realm", "Nexus Repository realm.")
  lazy val nexusHost         = SettingKey[Option[String]]("nexus-host", "Nexus Repository hostname.")
  lazy val nexusSnapshotRepo = SettingKey[Option[MavenRepository]]("nexus-snapshot", "Nexus Repository for Snapshots.")
  lazy val nexusReleaseRepo  = SettingKey[Option[MavenRepository]]("nexus-release", "Nexus Repository for Releases.")

  object ScalaToolsNexus {
    lazy val Realm        = "Sonatype Nexus Repository Manager"
    lazy val Host         = "nexus.scala-tools.org"
    lazy val SnapshotRepo = repo(Host, "snapshots")
    lazy val ReleaseRepo  = repo(Host, "releases")

    def repo(host: String, status: String) =
      "Nexus Repository for " + status.capitalize at "http://%s/content/repositories/%s".format(host, status)
  }

  object CredentialSources {
    lazy val Default = Path.userHome / ".sbt" / ".credentials"
    lazy val Maven   = Path.userHome / ".m2" / "settings.xml"
  }

  /**
    * Helper to setup loading credentials from Maven's `settings.xml` typically residing in `~/.m2/settings.xml`.
    *
    * @see http://maven.apache.org/settings.html
    */
  object MavenCredentials {

    def add(realm: String, host: String, settingsPath: File, log: Logger): Unit =
      loadCredentials(host, settingsPath) match {
        case Right(creds) => creds foreach { c => Credentials.add(realm, host, c._1, c._2) }
        case Left(err)    => log.warn(err)
      }

    private def loadCredentials(host: String, file: File): Either[String, Seq[(String, String)]] =
      if (file.exists)
        util.control.Exception.catching(classOf[org.xml.sax.SAXException], classOf[IOException]) either {
          (xml.XML.loadFile(file) \ "servers" \ "server") withFilter { s => (s \ "id").text == host } map { s =>
            (s \ "username" text, s \ "password" text)
          }
        } match {
          case Right(creds) => Right(creds)
          case Left(e)      => Left("Could not read the settings file %s [%s]".format(file, e.getMessage))
        }
      else Left("Maven settings file " + file + " does not exist")
  }

  /**
    * This is a modified version of `sbt.Defaults.ivySbt0` that prepends credentials from `mavenSettings`.
    */
  def loadIvySbt(conf: IvyConfiguration, creds: Seq[Credentials], mvnSettings: File, realm: String, host: Option[String], s: TaskStreams) = {
    if (mvnSettings.exists && host.isDefined) MavenCredentials.add(realm, host.get, mvnSettings, s.log)
    Credentials.register(creds, s.log)
    new IvySbt(conf)
  }

  def scalaToolsSettings: Seq[Setting[_]] =
    inConfig(ScalaTools)(Seq(
      nexusRealm        := ScalaToolsNexus.Realm,
      nexusHost         := Some(ScalaToolsNexus.Host),
      nexusSnapshotRepo := Some(ScalaToolsNexus.SnapshotRepo),
      nexusReleaseRepo  := Some(ScalaToolsNexus.ReleaseRepo),
      publishTo        <<= (isSnapshot, nexusSnapshotRepo, nexusReleaseRepo) { if (_) _ else _ },
      mavenSettings     := CredentialSources.Maven,
      credentials       := Seq(Credentials(CredentialSources.Default)),
      ivySbt           <<= (ivyConfiguration, credentials, mavenSettings, nexusRealm, nexusHost, streams) map loadIvySbt)) ++
    Seq(
      publishTo     <<= publishTo in ScalaTools,
      credentials  <++= credentials in ScalaTools,
      ivySbt        <<= ivySbt in ScalaTools)

}
