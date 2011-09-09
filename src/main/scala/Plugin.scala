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

  object DistributionRepositories {
    lazy val Local    = Resolver.file("Local Repository", Path.userHome / ".m2" / "repository" asFile)
    lazy val Snapshot = nexusRepo("snapshots")
    lazy val Release  = nexusRepo("releases")

    def nexusRepo(status: String) =
      "Nexus Repository for " + status.capitalize at "http://nexus.scala-tools.org/content/repositories/" + status
  }

  object CredentialSources {
    lazy val Default = Path.userHome / ".ivy2" / ".credentials"
    lazy val Maven   = Path.userHome / ".m2" / "settings.xml"
  }

  /**
    * Helper to setup loading credentials from Maven's `settings.xml` typically residing in `~/.m2/settings.xml`.
    *
    * @see http://maven.apache.org/settings.html
    */
  object MavenCredentials {
    val nexusRealm = "Sonatype Nexus Repository Manager"
    val nexusHost  = "nexus.scala-tools.org"

    def add(path: File, log: Logger): Unit =
      loadCredentials(path) match {
        case Right(dc) => dc map Credentials.toDirect foreach { c => Credentials.add(c.realm, c.host, c.userName, c.passwd) }
        case Left(err) => log.warn(err)
      }

    def loadCredentials(file: File): Either[String, Seq[Credentials]] =
      if (file.exists)
        util.control.Exception.catching(classOf[org.xml.sax.SAXException], classOf[IOException]) either {
          xml.XML.loadFile(file) \ "servers" \ "server" map { s =>
            // settings.xml doesn't keep auth realm but SBT expects one;
            // Set a realm for known host and fallback to something generic for others
            val (h, u, p) = (s \ "id" text, s \ "username" text, s \ "password" text)
            Credentials((if (h == nexusHost) nexusRealm else "Unknown"), h, u, p)
          }
        } match {
          case Right(creds) => Right(creds)
          case Left(e)      => Left("Could not read the settings file %s [%s]".format(file, e.getMessage))
        }
      else Left("Maven settings file " + file + " does not exist")
  }

  def switchPublishRepo(version: String) = {
    import DistributionRepositories._
    if (version endsWith "-SNAPSHOT") Some(Snapshot) else Some(Release)
  }

  def loadIvySbt(conf: IvyConfiguration, creds: Seq[Credentials], mvn: File, s: TaskStreams) = {
    if (mvn.exists) MavenCredentials.add(mvn, s.log)
    Credentials.register(creds, s.log)
    new IvySbt(conf)
  }

  def scalaToolsSettings: Seq[Setting[_]] =
    inConfig(ScalaTools)(Seq(
      publishTo    <<= version(switchPublishRepo),
      mavenSettings := CredentialSources.Maven,
      credentials   := Seq(Credentials(CredentialSources.Default)),
      ivySbt       <<= (ivyConfiguration, credentials, mavenSettings, streams) map loadIvySbt)) ++
    Seq(
      publishTo     <<= publishTo or (publishTo in ScalaTools),
      mavenSettings <<= mavenSettings or (mavenSettings in ScalaTools),
      credentials  <++= credentials in ScalaTools,
      ivySbt        <<= ivySbt in ScalaTools)

}
