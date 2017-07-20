package org.ergoplatform.nodeView.history.storage.modifierprocessors

import io.iohk.iodb.ByteArrayWrapper
import org.ergoplatform.modifiers.{ErgoFullBlock, ErgoPersistentModifier}
import org.ergoplatform.modifiers.history.{ADProofs, BlockTransactions, Header, HistoryModifierSerializer}
import scorex.core.NodeViewModifier._
import scorex.core.consensus.History.ProgressInfo
import scorex.core.utils.ScorexLogging

trait FullBlockProcessor extends HeadersProcessor with ScorexLogging {

  val BestFullBlockKey: ByteArrayWrapper = ByteArrayWrapper(Array.fill(32)(-1))

  def bestFullBlockId: Option[ModifierId] = historyStorage.db.get(BestFullBlockKey).map(_.data)

  protected def processFullBlock(header: Header,
                                 txs: BlockTransactions,
                                 adProofs: ADProofs,
                                 txsAreNew: Boolean): ProgressInfo[ErgoPersistentModifier] = {
    val newModRow = if (txsAreNew) {
      (ByteArrayWrapper(txs.id), ByteArrayWrapper(HistoryModifierSerializer.toBytes(txs)))
    } else {
      (ByteArrayWrapper(adProofs.id), ByteArrayWrapper(HistoryModifierSerializer.toBytes(adProofs)))
    }
    val storageVersion = if (txsAreNew) txs.id else adProofs.id
    val prevBestFullBlockId = bestFullBlockId.get
    val isNewBest = (scoreOf(prevBestFullBlockId), scoreOf(header.id)) match {
      case (_, None) => throw new Error("Score of best block is undefined")
      case (None, b) => true
      case (Some(prevBestScore), Some(curentScore)) if curentScore > prevBestScore => true
      case (Some(prevBestScore), Some(curentScore)) if curentScore == prevBestScore => false //TODO take block with more work in it
      case _ => false
    }
    if(isNewBest) {
      val fullBlock = ErgoFullBlock(header, txs, adProofs)
      historyStorage.insert(storageVersion, Seq(newModRow, (BestFullBlockKey, newModRow._1)))
      if((header.parentId sameElements prevBestFullBlockId) || bestFullBlockId.isEmpty) {
        log.info(s"Got new best header ${header.encodedId} with transactions and proofs")
        ProgressInfo(None, Seq(), Seq(fullBlock))
      } else {
        log.info(s"Process fork for new best header ${header.encodedId} with transactions and proofs")
        ???
      }
    } else {
      log.info(s"Got transactions and proofs for non-best header ${header.encodedId}")
      historyStorage.insert(storageVersion, Seq(newModRow))
      ProgressInfo(None, Seq(), Seq())
    }
  }

}
