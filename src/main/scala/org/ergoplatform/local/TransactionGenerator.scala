package org.ergoplatform.local

import akka.actor.{Actor, ActorRef, ActorRefFactory, Props}
import org.ergoplatform.local.TransactionGenerator.{Attempt, CheckGeneratingConditions, StartGeneration}
import org.ergoplatform.modifiers.mempool.ErgoTransaction
import org.ergoplatform.nodeView.history.ErgoHistory
import org.ergoplatform.nodeView.mempool.ErgoMemPool
import org.ergoplatform.nodeView.state.UtxoState
import org.ergoplatform.nodeView.wallet.{ErgoWallet, Pay2SAddress, PaymentRequest}
import org.ergoplatform.settings.TestingSettings
import scorex.core.NodeViewHolder.ReceivableMessages.{GetDataFromCurrentView, LocallyGeneratedTransaction}
import scorex.core.network.NodeViewSynchronizer.ReceivableMessages.SuccessfulTransaction
import scorex.core.utils.ScorexLogging
import sigmastate.Values

import scala.util.{Random, Try}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global


class TransactionGenerator(viewHolder: ActorRef,
                           settings: TestingSettings) extends Actor with ScorexLogging {


  private var isStarted = false

  private var transactionsPerBlock = 0
  private var currentFullHeight = 0

  override def receive: Receive = {
    case StartGeneration =>
      if (!isStarted) {
        viewHolder ! GetDataFromCurrentView[ErgoHistory, UtxoState, ErgoWallet, ErgoMemPool, Unit] { v =>
          currentFullHeight = v.history.headersHeight

          context.system.eventStream.subscribe(self, classOf[SuccessfulTransaction[ErgoTransaction]])

          context.system.scheduler.schedule(1500.millis,
            3000.millis)(self ! CheckGeneratingConditions)(context.system.dispatcher)
        }
      }
      isStarted = true

    case CheckGeneratingConditions =>
      viewHolder ! GetDataFromCurrentView[ErgoHistory, UtxoState, ErgoWallet, ErgoMemPool, Unit] { v =>
        val fbh = v.history.fullBlockHeight
        if (fbh > currentFullHeight) {
          currentFullHeight = fbh
          transactionsPerBlock = Random.nextInt(settings.maxTransactionsPerBlock) + 1

          self ! Attempt
        }
      }

    case Attempt =>
      //todo: real prop, assets
      transactionsPerBlock = transactionsPerBlock - 1
      if(transactionsPerBlock >= 0) {
        val newOutsCount = Random.nextInt(50) + 1
        val newOuts = (1 to newOutsCount).map { _ =>
          val value = Random.nextInt(50) + 1
          val prop = if (Random.nextBoolean()) Values.TrueLeaf else Values.FalseLeaf

          PaymentRequest(Pay2SAddress(prop), value, None, None)
        }

        viewHolder ! GetDataFromCurrentView[ErgoHistory, UtxoState, ErgoWallet, ErgoMemPool, Unit] { v =>
          v.vault.generateTransaction(newOuts).onComplete(t => self ! t.flatten)
        }
      }

    case txTry: Try[ErgoTransaction]@unchecked =>
      txTry.foreach { tx =>
        log.info("Locally generated tx: " + tx)
        viewHolder ! LocallyGeneratedTransaction[ErgoTransaction](tx)
      }

    case SuccessfulTransaction(_) => self ! Attempt
   }
}

object TransactionGenerator {

  case object StartGeneration

  case object CheckGeneratingConditions

  case object Attempt
}

object TransactionGeneratorRef {
  def props(viewHolder: ActorRef, settings: TestingSettings): Props =
    Props(new TransactionGenerator(viewHolder, settings))

  def apply(viewHolder: ActorRef, settings: TestingSettings)
           (implicit context: ActorRefFactory): ActorRef =
    context.actorOf(props(viewHolder, settings))

  def apply(viewHolder: ActorRef, settings: TestingSettings, name: String)
           (implicit context: ActorRefFactory): ActorRef =
    context.actorOf(props(viewHolder, settings), name)
}
