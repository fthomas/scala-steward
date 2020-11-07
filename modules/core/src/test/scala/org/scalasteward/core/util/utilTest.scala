package org.scalasteward.core.util
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import scala.collection.mutable.ListBuffer

class utilTest extends AnyFunSuite with Matchers with ScalaCheckPropertyChecks {
  test("appendBounded") {
    val lb = new ListBuffer[Int]
    lb.appendAll(List(1, 2, 3))

    appendBounded(lb, 4, 4)
    lb.toList shouldBe List(1, 2, 3, 4)

    appendBounded(lb, 5, 4)
    lb.toList shouldBe List(3, 4, 5)

    appendBounded(lb, 6, 4)
    lb.toList shouldBe List(3, 4, 5, 6)

    appendBounded(lb, 7, 6)
    lb.toList shouldBe List(3, 4, 5, 6, 7)

    appendBounded(lb, 8, 6)
    lb.toList shouldBe List(3, 4, 5, 6, 7, 8)

    appendBounded(lb, 9, 6)
    lb.toList shouldBe List(6, 7, 8, 9)
  }

  test("bindUntilTrue: empty list") {
    bindUntilTrue(List.empty[Option[Boolean]]) shouldBe Some(false)
  }

  test("intersects") {
    intersects(List(1, 3, 5), Vector(2, 4, 6)) shouldBe false
    intersects(List(1, 3, 5), Vector(2, 3, 6)) shouldBe true
  }

  test("combineMapLastWin") {
    val a = Map("foo" -> "https://github.com/foo/foo", "bar" -> "https://bar.example.com")
    val b = Map("bar" -> "https://github.com/bar/bar", "buz" -> "https://bitbucket.org/buz/buz")
    combineMapLastWin(a, b) shouldBe Map(
      "foo" -> "https://github.com/foo/foo",
      "bar" -> "https://github.com/bar/bar", // last win
      "buz" -> "https://bitbucket.org/buz/buz"
    )
  }
}
