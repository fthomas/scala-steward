package org.scalasteward.core.git

import org.scalacheck.{Arbitrary, Gen}
import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.data.Update
import org.scalasteward.core.data.Update.Single
import org.scalasteward.core.repoconfig.CommitsConfig
import org.scalasteward.core.update.show
import org.scalasteward.core.util.Nel
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class gitTest extends AnyFunSuite with Matchers with ScalaCheckPropertyChecks {
  implicit val updateArbitrary: Arbitrary[Update] = Arbitrary(for {
    groupId <- Gen.alphaStr
    artifactId <- Gen.alphaStr
    currentVersion <- Gen.alphaStr
    newerVersion <- Gen.alphaStr
  } yield Single(groupId % artifactId % currentVersion, Nel.one(newerVersion)))

  test("commitMsgFor should work with static message") {
    val commitsConfig = CommitsConfig(Some("Static message"))
    forAll { update: Update =>
      commitMsgFor(update, commitsConfig) shouldBe "Static message"
    }
  }

  test("commitMsgFor should work with default message") {
    val commitsConfig = CommitsConfig(Some("${default}"))
    forAll { update: Update =>
      commitMsgFor(update, commitsConfig) shouldBe s"Update ${show.oneLiner(update)} to ${update.nextVersion}"
    }
  }

  test("commitMsgFor should work with templated message") {
    val commitsConfig =
      CommitsConfig(
        Some("Update ${artifactName} from ${currentVersion} to ${nextVersion}")
      )
    forAll { update: Update =>
      commitMsgFor(update, commitsConfig) shouldBe s"Update ${show.oneLiner(
        update
      )} from ${update.currentVersion} to ${update.nextVersion}"
    }
  }

  test("commitMsgFor should work with templated message - non-default branch") {
    val commitsConfig =
      CommitsConfig(
        Some("Update ${artifactName} from ${currentVersion} to ${nextVersion} (${branchName})")
      )
    forAll { update: Update =>
      val branch = Branch("non-default")
      commitMsgFor(update, commitsConfig, Some(branch)) shouldBe s"Update ${show.oneLiner(
        update
      )} from ${update.currentVersion} to ${update.nextVersion} (${branch.name})"
    }
  }
}
