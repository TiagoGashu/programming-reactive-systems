package com.gashu.counter

import akka.actor.{Actor, ActorSystem, Props}

/**
 * @author tiagogashu on 23/05/19
 **/
class StatefulCounter extends Actor {
    var count = 0

    def receive = {
        case "incr" => count += 1
        case "get" => sender ! count
    }
}

class StatefulCounterMain extends Actor {
    private val statefulCounter = context.actorOf(Props[StatefulCounter], "statefulCounter")

    def receive = {
        case "startCounting" =>
            statefulCounter ! "incr"
            statefulCounter ! "incr"
            statefulCounter ! "get"
        case count: Int =>
            println(s"Received the count: $count")
            context.stop(self)
    }

}

object Main extends App {
    val system: ActorSystem = ActorSystem("akka-system")

    val statefulCounterMain = system.actorOf(Props[StatefulCounterMain], "statefulCounterMain")

    statefulCounterMain ! "startCounting"

}