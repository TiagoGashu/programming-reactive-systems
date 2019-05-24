package com.gashu.bankaccount

import akka.actor.{Actor, ActorSystem, Props}

/**
  * @author tiagogashu on 23/05/19
  **/
object BankAccountCase {
  case class CreateAccount(initialAmount: BigInt) {
    require(initialAmount > 0)
  }
  case class Deposit(amount: BigInt) {
    require(amount > 0)
  }
  case class Withdraw(amount: BigInt) {
    require(amount > 0)
  }
  case object BankStatement
  case class BankStatementRet(amount: BigInt)
  case class Done(message: String)
  case class Failed(message: String)
}

class BankAccountCase extends Actor {
  import BankAccountCase._

  var balance = BigInt(0)

  def receive: Receive = {
    case CreateAccount(initialAmount) => {
      balance = initialAmount
      sender ! BankAccountCase.Done(s"Created account with: $initialAmount")
    }
    case Deposit(amount) => {
      balance += amount
      sender ! BankAccountCase.Done(s"Deposited $amount")
    }
    case Withdraw(amount) if amount <= balance => {
      balance -= amount
      sender ! BankAccountCase.Done(s"Withdrawed $amount")
    }
    case BankStatement => {
      sender ! BankAccountCase.Done(s"Bank statement: $balance")
    }
    case _ => sender ! BankAccountCase.Failed(s"Operation not supported")
  }
}

class BankAccountMain extends Actor {
  import BankAccountCase._

  val bankAccount = context.actorOf(Props[BankAccountCase], "bankAccount")

  def receive: Receive = {
    case "start" =>
      bankAccount ! CreateAccount(50)
      bankAccount ! Deposit(100)
      bankAccount ! Withdraw(10)
      bankAccount ! BankStatement
    case BankAccountCase.Done(message) =>
      println(message)
    case BankAccountCase.Failed(message: String) =>
      println(message)
      context.stop(self)
  }
}

object Main3 extends App {
  val system: ActorSystem = ActorSystem("akka-system")

  val bankAccountMain = system.actorOf(Props[BankAccountMain], "bankAccountMain")

  bankAccountMain ! "start"
}

