package reactive

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl._

object StashBufferDemo {
    val initial = Behaviors.setup[String] { ctx =>
        val buffer = StashBuffer[String](100)

        Behaviors.receiveMessage {
            case "first" =>
                buffer.unstashAll(ctx, running)
            case other =>
                buffer.stash(other)
                Behaviors.same
        }
    }

    val running = Behaviors.receiveMessage[String] { msg =>
        println(s"Hello $msg!")
        if (msg == "stop") Behaviors.stopped else Behaviors.same
    }

    def main(args: Array[String]): Unit = {
        val system = ActorSystem(initial, "StashBuffer")
        system ! "World"
        system ! "fancy" // will not be processed
        system ! "stop"
        system ! "first"
    }
}
