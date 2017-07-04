package org.ergoplatform.modifiers.experimental

import io.circe.Json
import org.ergoplatform.settings.Constants
import scorex.core.NodeViewModifier
import scorex.core.NodeViewModifier.{ModifierId, ModifierTypeId}
import scorex.core.serialization.Serializer

case class MinimalHeader(payloadRootHash: Array[Byte],
                         nonce: Int) extends NodeViewModifier {

  override val modifierTypeId: ModifierTypeId = MinimalHeader.ModifierTypeId

  override lazy val id: ModifierId = Constants.hash(bytes)

  override lazy val json: Json = ???


  override type M = MinimalHeader

  override lazy val serializer: Serializer[MinimalHeader] = ???

}

object MinimalHeader {
  val ModifierTypeId: Byte = 100: Byte
}
