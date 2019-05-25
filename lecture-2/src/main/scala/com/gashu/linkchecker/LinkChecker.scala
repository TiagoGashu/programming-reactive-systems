package com.gashu.linkchecker

import akka.actor.{Actor, ActorSystem, Props, ReceiveTimeout}

import scala.concurrent.duration._

/**
  * @author tiagogashu on 25/05/19
  **/


class LinkChecker extends Actor {

  import Receptionist._

  private val receptionist = context.actorOf(Props[Receptionist], "receptionist")
  context.watch(receptionist) // sign death pact

  receptionist ! Get("http://www.google.com")
  receptionist ! Get("http://www.google.com/1")
  receptionist ! Get("http://www.google.com/2")
  receptionist ! Get("http://www.google.com/3")
  receptionist ! Get("http://www.google.com/4")
  receptionist ! Get("http://www.google.com")

  context.setReceiveTimeout(10.seconds)

  def receive = {
    case Result(url, set) =>
      println(set.toVector.sorted.mkString(s"Results for '$url':\n", "\n", "\n"))
    case Failed(url, reason) =>
      println(s"Failed to fetch '$url': $reason\n")
    case ReceiveTimeout =>
      context.stop(self)
  }

  override def postStop(): Unit = {
    HttpClient.shutdown()
  }

}

object LinkCheckerMain extends App {
  val system: ActorSystem = ActorSystem("akka-system")

  val client = system.actorOf(Props[LinkChecker], "linkChecker")
}