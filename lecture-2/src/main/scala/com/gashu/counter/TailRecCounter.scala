package com.gashu.counter

import akka.actor.{Actor, ActorSystem, Props}

/**
  * @author tiagogashu on 23/05/19
  **/
class TailRecCounter extends Actor {

  def count(counter: Int): Receive = {
    case "incr" => context.become(count(counter + 1))
    case "get" => sender ! counter
  }

  def receive = count(0)

}

class TailRecCounterMain extends Actor {

  private val tailRecCounter = context.actorOf(Props[TailRecCounter], "tailRecCounter")

  def receive: Receive = {
    case "start" =>
      tailRecCounter ! "incr"
      tailRecCounter ! "incr"
      tailRecCounter ! "incr"
      tailRecCounter ! "get"
    case count: Int =>
      println(s"Received count: $count")
      context.stop(self)
  }

}

object Main2 extends App {
  val system: ActorSystem = ActorSystem("akka-system")

  val tailRecCounterMain = system.actorOf(Props[TailRecCounterMain], "tailRecCounterMain")

  tailRecCounterMain ! "start"
}
