package org.scalasteward.core.edit

import io.circe.{Decoder, Encoder}
import org.scalasteward.core.data.GroupId
import org.scalasteward.core.util.Nel
import io.circe.generic.semiauto._

case class CustomHeuristic(
    groupId: GroupId,
    artifactName: Option[String],
    termsToReplace: Nel[String]
)

object CustomHeuristic {

  implicit val decoder: Decoder[CustomHeuristic] =
    deriveDecoder

  implicit val encoder: Encoder[CustomHeuristic] =
    deriveEncoder

}
