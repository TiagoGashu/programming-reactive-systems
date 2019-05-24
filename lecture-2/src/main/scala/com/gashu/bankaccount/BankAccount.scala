package com.gashu.bankaccount

import akka.actor.{Actor, ActorSystem, Props}

/**
  * @author tiagogashu on 23/05/19
  **/
object BankAccount {
  case class CreateAccount(initialAmount: BigInt) {
    require(initialAmount > 0)
  }
  case class Deposit(amount: BigInt) {
    require(amount > 0)
  }
  case class Withdraw(amount: BigInt) {
    require(amount > 0)
  }
  case object FetchBankStatement
  case class BankStatement(balance: BigInt)
  case class Done(message: String)
  case class Failed(message: String)
}

class BankAccount extends Actor {
  import BankAccount._

  var balance = BigInt(0)

  def receive: Receive = {
    case CreateAccount(initialBalance) => {
      balance = initialBalance
      sender ! Done(s"Created account with: $initialBalance")
    }
    case Deposit(amount) => {
      balance += amount
      sender ! Done(s"Deposited $amount")
    }
    case Withdraw(amount) if amount <= balance => {
      balance -= amount
      sender ! Done(s"Withdrawed $amount")
    }
    case FetchBankStatement => {
      sender ! BankStatement(balance)
    }
    case _ => sender ! Failed(s"Operation not supported")
  }
}

class BankAccountMain extends Actor {
  import BankAccount._

  private val bankAccount = context.actorOf(Props[BankAccount], "bankAccount")

  def receive: Receive = {
    case "start" =>
      bankAccount ! CreateAccount(50)
      bankAccount ! Deposit(100)
      bankAccount ! Withdraw(10)
      bankAccount ! FetchBankStatement
    case Done(message) =>
      println(message)
    case Failed(reason: String) =>
      println(reason)
      context.stop(self)
    case BankStatement(balance) =>
      println(s"The balance is $balance")
      context.stop(self)
  }
}

object Main3 extends App {
  val system: ActorSystem = ActorSystem("akka-system")

  val bankAccountMain = system.actorOf(Props[BankAccountMain], "bankAccountMain")

  bankAccountMain ! "start"
}

