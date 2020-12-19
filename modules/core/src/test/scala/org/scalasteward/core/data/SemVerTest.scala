package org.scalasteward.core.data

import eu.timepit.refined.types.numeric.NonNegBigInt
import eu.timepit.refined.types.string.NonEmptyString
import org.scalasteward.core.data.SemVer.Change
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class SemVerTest extends AnyFunSuite with Matchers {
  implicit val toNonNegBigInt: Int => NonNegBigInt =
    i => NonNegBigInt.unsafeFrom(BigInt(i))

  implicit val toNonEmptyString: String => NonEmptyString =
    NonEmptyString.unsafeFrom

  test("parse: simple examples") {
    SemVer.parse("1.2.3") shouldBe Some(SemVer(1, 2, 3, None, None))
    SemVer.parse("0.0.0") shouldBe Some(SemVer(0, 0, 0, None, None))
    SemVer.parse("123.456.789") shouldBe Some(SemVer(123, 456, 789, None, None))
  }

  test("parse: with pre-release identifier") {
    SemVer.parse("1.0.0-SNAP5") shouldBe Some(SemVer(1, 0, 0, Some("SNAP5"), None))
    SemVer.parse("9.10.100-0.3.7") shouldBe Some(SemVer(9, 10, 100, Some("0.3.7"), None))
  }

  test("parse: empty pre-release identifier") {
    SemVer.parse("5.6.7-") shouldBe None
  }

  test("parse: with build metadata") {
    SemVer.parse("1.0.0+20130313144700") shouldBe
      Some(SemVer(1, 0, 0, None, Some("20130313144700")))
    SemVer.parse("1.0.0-beta+exp.sha.5114f85") shouldBe
      Some(SemVer(1, 0, 0, Some("beta"), Some("exp.sha.5114f85")))
  }

  test("parse: empty build metadata") {
    SemVer.parse("6.7.8+") shouldBe None
    SemVer.parse("9.10.11-M1+") shouldBe None
  }

  test("parse: negative numbers") {
    SemVer.parse("-1.0.0") shouldBe None
    SemVer.parse("0.-1.0") shouldBe None
    SemVer.parse("0.0.-1") shouldBe None
  }

  test("parse: leading zeros") {
    SemVer.parse("01.0.0") shouldBe None
    SemVer.parse("0.01.0") shouldBe None
    SemVer.parse("0.0.01") shouldBe None
  }

  test("getChange") {
    SemVer.getChange(SemVer(1, 3, 4, None, None), SemVer(2, 1, 2, None, None)) shouldBe
      Some(Change.Major)
    SemVer.getChange(SemVer(2, 3, 4, None, None), SemVer(2, 5, 2, None, None)) shouldBe
      Some(Change.Minor)
    SemVer.getChange(
      SemVer(2, 3, 4, Some("SNAP1"), None),
      SemVer(2, 3, 4, Some("SNAP2"), None)
    ) shouldBe
      Some(Change.PreRelease)
    SemVer.getChange(
      SemVer(2, 3, 4, Some("M1"), Some("1")),
      SemVer(2, 3, 4, Some("M1"), Some("2"))
    ) shouldBe
      Some(Change.BuildMetadata)
    SemVer.getChange(
      SemVer(2, 3, 4, Some("M1"), None),
      SemVer(2, 3, 4, Some("M1"), None)
    ) shouldBe None
    SemVer.getChange(SemVer(0, 20, 0, Some("M4"), None), SemVer(0, 20, 3, None, None)) shouldBe
      Some(Change.Minor)
    SemVer.getChange(SemVer(0, 1, 0, None, None), SemVer(0, 2, 0, None, None)) shouldBe
      Some(Change.Major)
    SemVer.getChange(SemVer(0, 0, 1, None, None), SemVer(0, 0, 2, None, None)) shouldBe
      Some(Change.Major)
    SemVer.getChange(SemVer(0, 0, 0, None, None), SemVer(0, 0, 1, None, None)) shouldBe
      Some(Change.Major)
    SemVer.getChange(SemVer(0, 0, 0, None, None), SemVer(0, 0, 0, None, None)) shouldBe
      None
  }
}
