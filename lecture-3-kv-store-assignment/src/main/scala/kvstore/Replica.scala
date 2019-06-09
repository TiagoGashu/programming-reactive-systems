package kvstore

import akka.actor.{Actor, ActorRef, OneForOneStrategy, PoisonPill, Props, SupervisorStrategy, Terminated}
import kvstore.Arbiter._
import akka.pattern.{ask, pipe}
import akka.stream.Supervision

import scala.concurrent.duration._
import akka.util.Timeout

object Replica {
  sealed trait Operation {
    def key: String
    def id: Long
  }
  case class Insert(key: String, value: String, id: Long) extends Operation
  case class Remove(key: String, id: Long) extends Operation
  case class Get(key: String, id: Long) extends Operation

  sealed trait OperationReply
  case class OperationAck(id: Long) extends OperationReply
  case class OperationFailed(id: Long) extends OperationReply
  case class GetResult(key: String, valueOption: Option[String], id: Long) extends OperationReply

  def props(arbiter: ActorRef, persistenceProps: Props): Props = Props(new Replica(arbiter, persistenceProps))
}

class Replica(val arbiter: ActorRef, persistenceProps: Props) extends Actor {
  import Replica._
  import Replicator._
  import Persistence._
  import context.dispatcher

  /*
   * The contents of this actor is just a suggestion, you can implement it in any way you like.
   */
  
  var kv = Map.empty[String, String]
  // a map from secondary replicas to replicators
  var secondaries = Map.empty[ActorRef, ActorRef]
  // the current set of replicators
  var replicators = Set.empty[ActorRef]

  val persister = context.actorOf(persistenceProps)
  context.watch(persister)
  // supervision strategy
  override val supervisorStrategy = OneForOneStrategy(maxNrOfRetries = 2) {
    case _: PersistenceException => SupervisorStrategy.Restart
  }

  // keeps the requesters mapped by id
  var requesters = Map.empty[Long, ActorRef]
  // maps from seq to sender
  var requestersSnaps = Map.empty[Long, ActorRef]
  var expectedSeq: Long = 0L

  arbiter ! Join

  def receive = {
    case JoinedPrimary   => context.become(leader)
    case JoinedSecondary => context.become(replica)
  }

  val leader: Receive = {
    case Replicas(replicas) =>
      val secondaryReplicas = replicas - this.self
      val newReplicas = secondaryReplicas diff secondaries.keySet
      val deletedReplicas = secondaries.keySet diff secondaryReplicas
      newReplicas foreach createReplicator
      deletedReplicas foreach deleteReplicator
    case Insert(k, v, id) => update(k, Some(v), id)
    case Remove(k, id) => update(k, None, id)
    case Get(k, id) =>
      val value = if(kv contains k) Some(kv(k)) else None
      sender ! GetResult(k, value, id)
    case Persisted(_, id) =>
      val requester = requesters(id)
      requesters = requesters - id
      requester ! OperationAck(id)
  }

  private def update(k: String, v: Option[String], id: Long) = {
    requesters += (id -> sender)
    if(v.isEmpty) kv -= k
    else kv += (k -> v.get)
    persister ! Persist(k, v, id)
    replicators foreach { _ ! Replicate(k, v, id) }
  }

  private def createReplicator(replica: ActorRef) = {
    val newReplicator = context.actorOf(Replicator.props(replica))
    secondaries += (replica -> newReplicator)
    replicators += newReplicator
    kv foreach { case (k, v) => newReplicator ! Replicate(k, Some(v), -1) }
  }

  private def deleteReplicator(delReplica: ActorRef) = {
    val deletedReplicator = secondaries(delReplica)
    secondaries -= delReplica
    replicators -= deletedReplicator
  }

  val replica: Receive = {
    case Get(k, id) =>
      val value = if(kv contains k) Some(kv(k)) else None
      sender ! GetResult(k, value, id)
    case Snapshot(k, v, seq) =>
      if(expectedSeq == -1) {
        expectedSeq = seq
      }

      if(seq < expectedSeq) {
        sender ! SnapshotAck(k, seq)
      }
      else if(seq == expectedSeq) {
        expectedSeq += 1
        if (v.isEmpty) kv -= k
        else kv += (k -> v.get)
        requestersSnaps += (seq -> sender)
        persister ! Persist(k, v, seq)
      }
    case Persisted(k, seq) =>
      val requester = requestersSnaps(seq)
      requestersSnaps -= seq
      requester ! SnapshotAck(k, seq)
  }

}

