package com.gashu.counter;

import akka.actor.{ Actor, ActorLogging, ActorRef, ActorSystem, Props }

/**
 * @author tiagogashu on 23/05/19
 **/
public class StatefulCounter extends Actor {
    var count = 0

    def receive = {
        case "incr" => count += 1
    }
}
