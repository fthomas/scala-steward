package org.scalasteward.core.data

import munit.FunSuite
import org.scalasteward.core.data.SemVerSpec.Change

class SemVerSpecTest extends FunSuite {
  test("parse: simple examples") {
    assertEquals(SemVerSpec.parse("1.2.3"), Some(SemVerSpec("1", "2", "3")))
    assertEquals(SemVerSpec.parse("0.0.0"), Some(SemVerSpec("0", "0", "0")))
    assertEquals(SemVerSpec.parse("123.456.789"), Some(SemVerSpec("123", "456", "789")))
  }

  test("parse: with pre-release identifier") {
    assertEquals(SemVerSpec.parse("1.0.0-SNAP5"), Some(SemVerSpec("1", "0", "0", Some("SNAP5"))))
    assertEquals(
      SemVerSpec.parse("9.10.100-0.3.7"),
      Some(SemVerSpec("9", "10", "100", Some("0.3.7")))
    )
  }

  test("parse: empty pre-release identifier") {
    assertEquals(SemVerSpec.parse("5.6.7-"), None)
  }

  test("parse: with build metadata") {
    assertEquals(
      SemVerSpec.parse("1.0.0+20130313144700"),
      Some(SemVerSpec("1", "0", "0", None, Some("20130313144700")))
    )
    assertEquals(
      SemVerSpec.parse("1.0.0-beta+exp.sha.5114f85"),
      Some(SemVerSpec("1", "0", "0", Some("beta"), Some("exp.sha.5114f85")))
    )
  }

  test("parse: empty build metadata") {
    assertEquals(SemVerSpec.parse("6.7.8+"), None)
    assertEquals(SemVerSpec.parse("9.10.11-M1+"), None)
  }

  test("parse: negative numbers") {
    assertEquals(SemVerSpec.parse("-1.0.0"), None)
    assertEquals(SemVerSpec.parse("0.-1.0"), None)
    assertEquals(SemVerSpec.parse("0.0.-1"), None)
  }

  test("parse: leading zeros") {
    assertEquals(SemVerSpec.parse("01.0.0"), None)
    assertEquals(SemVerSpec.parse("0.01.0"), None)
    assertEquals(SemVerSpec.parse("0.0.01"), None)
  }

  test("getChange") {
    List(
      (SemVerSpec("1", "3", "4"), SemVerSpec("2", "1", "2"), Some(Change.Major)),
      (SemVerSpec("2", "3", "4"), SemVerSpec("2", "5", "2"), Some(Change.Minor)),
      (
        SemVerSpec("2", "3", "4", Some("SNAP1")),
        SemVerSpec("2", "3", "4", Some("SNAP2")),
        Some(Change.PreRelease)
      ),
      (
        SemVerSpec("2", "3", "4", Some("M1"), Some("1")),
        SemVerSpec("2", "3", "4", Some("M1"), Some("2")),
        Some(Change.BuildMetadata)
      ),
      (SemVerSpec("2", "3", "4", Some("M1")), SemVerSpec("2", "3", "4", Some("M1")), None),
      (SemVerSpec("0", "20", "0", Some("M4")), SemVerSpec("0", "20", "3"), Some(Change.PreRelease))
    ).foreach { case (from, to, result) =>
      assertEquals(SemVerSpec.getChange(from, to), result)
    }
  }
}
