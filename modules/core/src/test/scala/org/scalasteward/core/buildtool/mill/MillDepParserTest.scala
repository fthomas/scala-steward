package org.scalasteward.core.buildtool.mill

import org.scalasteward.core.data.{ArtifactId, Dependency, GroupId}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class MillDepParserTest extends AnyFunSuite with Matchers {
  test("parse dependencies from https://github.com/lihaoyi/requests-scala") {
    val data =
      """{"modules":[{"name":"requests[2.12.6]","repositories":[{"url":"https://repo1.maven.org/maven2","type":"maven","auth":null},{"url":"https://oss.sonatype.org/content/repositories/releases","type":"maven","auth":null}],"dependencies":[{"groupId":"com.lihaoyi","artifactId":"geny_2.12","version":"0.6.0"}]},{"name":"requests[2.12.6].test","repositories":[{"url":"https://repo1.maven.org/maven2","type":"maven","auth":null},{"url":"https://oss.sonatype.org/content/repositories/releases","type":"maven","auth":null}],"dependencies":[{"groupId":"com.lihaoyi","artifactId":"utest_2.12","version":"0.7.3"},{"groupId":"com.lihaoyi","artifactId":"ujson_2.12","version":"1.1.0"}]},{"name":"requests[2.13.0]","repositories":[{"url":"https://repo1.maven.org/maven2","type":"maven","auth":null},{"url":"https://oss.sonatype.org/content/repositories/releases","type":"maven","auth":null}],"dependencies":[{"groupId":"com.lihaoyi","artifactId":"geny_2.13","version":"0.6.0"}]},{"name":"requests[2.13.0].test","repositories":[{"url":"https://repo1.maven.org/maven2","type":"maven","auth":null},{"url":"https://oss.sonatype.org/content/repositories/releases","type":"maven","auth":null}],"dependencies":[{"groupId":"com.lihaoyi","artifactId":"utest_2.13","version":"0.7.3"},{"groupId":"com.lihaoyi","artifactId":"ujson_2.13","version":"1.1.0"}]}]}"""
    val result = parser.parseModules(data).fold(fail(_), identity)

    val dep12 = List(
      Dependency(GroupId("com.lihaoyi"), ArtifactId(s"geny_2.12"), "0.6.0")
    )

    assert(result.headOption.map(_.dependencies) === Some(dep12))

    val dep13 = List(
      Dependency(GroupId("com.lihaoyi"), ArtifactId(s"geny_2.13"), "0.6.0")
    )

    assert(result.find(_.name == "requests[2.13.0]").map(_.dependencies) === Some(dep13))
  }
}
