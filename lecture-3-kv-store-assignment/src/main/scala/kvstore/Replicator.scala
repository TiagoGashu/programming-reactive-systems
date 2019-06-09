package kvstore

import akka.actor.Props
import akka.actor.Actor
import akka.actor.ActorRef
import kvstore.Persistence.Persisted
import kvstore.Replica.OperationAck

import scala.concurrent.duration._

object Replicator {
  case class Replicate(key: String, valueOption: Option[String], id: Long)
  case class Replicated(key: String, id: Long)
  
  case class Snapshot(key: String, valueOption: Option[String], seq: Long)
  case class SnapshotAck(key: String, seq: Long)

  def props(replica: ActorRef): Props = Props(new Replicator(replica))
}

class Replicator(val replica: ActorRef) extends Actor {
  import Replicator._
  import context.dispatcher
  
  /*
   * The contents of this actor is just a suggestion, you can implement it in any way you like.
   */

  // map from sequence number to pair of sender and request
  var acks = Map.empty[Long, (ActorRef, Replicate)]
  // a sequence of not-yet-sent snapshots (you can disregard this if not implementing batching)
  var pending = Vector.empty[Snapshot]

  var _seqCounter = 0L
  def nextSeq() = {
    val ret = _seqCounter
    _seqCounter += 1
    ret
  }

  def receive: Receive = {
    case op: Replicate =>
      val seq = nextSeq()
      val tuple = (sender, op)
      acks += (seq -> tuple)
      replica ! Snapshot(op.key, op.valueOption, seq)
    case SnapshotAck(k, seq) =>
      println("Received snapshot ack from secondary replica")
      val (primary, op) = acks(seq)
      acks -= seq
      primary ! Replicated(k, op.id)
  }

}
