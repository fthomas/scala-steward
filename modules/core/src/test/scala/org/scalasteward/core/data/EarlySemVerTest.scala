package org.scalasteward.core.data

import munit.FunSuite
import org.scalasteward.core.data.EarlySemVer.Change

class EarlySemVerTest extends FunSuite {
  test("parse: simple examples") {
    assertEquals(EarlySemVer.parse("1.2.3"), Some(EarlySemVer("1", "2", "3")))
    assertEquals(EarlySemVer.parse("0.0.0"), Some(EarlySemVer("0", "0", "0")))
    assertEquals(EarlySemVer.parse("123.456.789"), Some(EarlySemVer("123", "456", "789")))
  }

  test("parse: with pre-release identifier") {
    assertEquals(EarlySemVer.parse("1.0.0-SNAP5"), Some(EarlySemVer("1", "0", "0", Some("SNAP5"))))
    assertEquals(
      EarlySemVer.parse("9.10.100-0.3.7"),
      Some(EarlySemVer("9", "10", "100", Some("0.3.7")))
    )
  }

  test("parse: empty pre-release identifier") {
    assertEquals(EarlySemVer.parse("5.6.7-"), None)
  }

  test("parse: with build metadata") {
    assertEquals(
      EarlySemVer.parse("1.0.0+20130313144700"),
      Some(EarlySemVer("1", "0", "0", None, Some("20130313144700")))
    )
    assertEquals(
      EarlySemVer.parse("1.0.0-beta+exp.sha.5114f85"),
      Some(EarlySemVer("1", "0", "0", Some("beta"), Some("exp.sha.5114f85")))
    )
  }

  test("parse: empty build metadata") {
    assertEquals(EarlySemVer.parse("6.7.8+"), None)
    assertEquals(EarlySemVer.parse("9.10.11-M1+"), None)
  }

  test("parse: negative numbers") {
    assertEquals(EarlySemVer.parse("-1.0.0"), None)
    assertEquals(EarlySemVer.parse("0.-1.0"), None)
    assertEquals(EarlySemVer.parse("0.0.-1"), None)
  }

  test("parse: leading zeros") {
    assertEquals(EarlySemVer.parse("01.0.0"), None)
    assertEquals(EarlySemVer.parse("0.01.0"), None)
    assertEquals(EarlySemVer.parse("0.0.01"), None)
  }

  test("getChange") {
    List(
      (EarlySemVer("1", "3", "4"), EarlySemVer("2", "1", "2"), Some(Change.Major)),
      (EarlySemVer("2", "3", "4"), EarlySemVer("2", "5", "2"), Some(Change.Minor)),
      (
        EarlySemVer("2", "3", "4", Some("SNAP1")),
        EarlySemVer("2", "3", "4", Some("SNAP2")),
        Some(Change.PreRelease)
      ),
      (
        EarlySemVer("2", "3", "4", Some("M1"), Some("1")),
        EarlySemVer("2", "3", "4", Some("M1"), Some("2")),
        Some(Change.BuildMetadata)
      ),
      (EarlySemVer("2", "3", "4", Some("M1")), EarlySemVer("2", "3", "4", Some("M1")), None),
      (
        EarlySemVer("0", "20", "0", Some("M4")),
        EarlySemVer("0", "20", "3"),
        Some(Change.Minor)
      ),
      (
        EarlySemVer("0", "1", "0", None, None),
        EarlySemVer("0", "2", "0", None, None),
        Some(Change.Major)
      ),
      (
        EarlySemVer("0", "0", "1", None, None),
        EarlySemVer("0", "0", "2", None, None),
        Some(Change.Major)
      ),
      (
        EarlySemVer("0", "0", "0", None, None),
        EarlySemVer("0", "0", "1", None, None),
        Some(Change.Major)
      ),
      (
        EarlySemVer("0", "0", "0", None, None),
        EarlySemVer("0", "0", "0", None, None),
        None
      )
    ).foreach { case (from, to, result) =>
      assertEquals(EarlySemVer.getChange(from, to), result)
    }
  }
}
