package org.scalasteward.core.buildtool.mill

import munit.FunSuite

class MillVersionParserTest extends FunSuite {
  val data = Seq(
    "" -> None,
    "0.6.0" -> Some("0.6.0"),
    "0.9.6-16-a5da34" -> Some("0.9.6-16-a5da34"),
    """0.6.0
      |""".stripMargin -> Some("0.6.0"),
    "\n\r0.6.0\n\r".stripMargin -> Some("0.6.0"),
    " 0.6.0 " -> Some("0.6.0")
  )

  for {
    (versionFileContent, expected) <- data
  } yield test(
    s"parse version from .mill-version file with content '${versionFileContent.replace("\n", "\n")}'"
  ) {
    val parsed = parser.parseMillVersion(versionFileContent)
    assertEquals(parsed, expected)
  }
}
