package com.gashu.bankaccount

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.event.LoggingReceive
import com.gashu.bankaccount.BankAccount.CreateAccount

/**
  * @author tiagogashu on 23/05/19
  **/
object WireTransfer {
  case class Transfer(from: ActorRef, to: ActorRef, amount: BigInt)
  case object Done
  case object Failed
}

class WireTransfer extends Actor with ActorLogging{
  import WireTransfer._

  def receive: Receive = {
    case Transfer(from, to, amount) =>
      from ! BankAccount.Withdraw(amount)
      context.become(awaitWithdraw(to, amount, sender))
  }

  def awaitWithdraw(to: ActorRef, amount: BigInt, client: ActorRef): Receive = {
    case BankAccount.Done(message) =>
      log.info(message)
      to ! BankAccount.Deposit(amount)
      context.become(awaitDeposit(client))
    case BankAccount.Failed =>
      client ! Failed
      context.stop(self)
  }

  def awaitDeposit(client: ActorRef): Receive = {
    case BankAccount.Done(message) =>
      log.info(message)
      client ! Done
  }

}

class Client extends Actor with ActorLogging {
  import WireTransfer._

  private val bankAccount1 = context.actorOf(Props[BankAccount], "bankAccount1")
  private val bankAccount2 = context.actorOf(Props[BankAccount], "bankAccount2")

  def receive: Receive = {
    case "doWireTransfer" =>
      bankAccount1 ! CreateAccount(100)
      context.become(transferAfterCreationOfAccount)
  }

  def transferAfterCreationOfAccount: Receive = {
    case BankAccount.Done(message) => {
      log.info(message)

      val transaction = context.actorOf(Props[WireTransfer], "wireTransfer")
      // transfers from account1 to account 2
      transaction ! Transfer(bankAccount1, bankAccount2, 50)

      // receives the commit of transaction
      context.become(LoggingReceive {
        case Done =>
          log.info("Wire transfer completed!")
          context.stop(self)
      })

    }
  }

}

object MainWireTransfer extends App {
  val system: ActorSystem = ActorSystem("akka-system")

  val client = system.actorOf(Props[Client], "client")

  client ! "doWireTransfer"
}
