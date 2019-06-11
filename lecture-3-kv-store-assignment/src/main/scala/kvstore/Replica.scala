package kvstore

import akka.actor.{Actor, ActorRef, Cancellable, OneForOneStrategy, PoisonPill, Props, ReceiveTimeout, SupervisorStrategy, Terminated}
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
  override val supervisorStrategy = OneForOneStrategy(maxNrOfRetries = 3) {
    case _: PersistenceException => SupervisorStrategy.Restart
  }

  // keeps the requesters mapped by id
  var requesters = Map.empty[Long, ActorRef]

  // maps from id to tuple (first value is primary and second is all replicas)
  var completeness = Map.empty[Long, (Boolean, Boolean)]

  // maps from seq to sender
  var requestersSnaps = Map.empty[Long, (ActorRef, Persist)]

  var pendingPersists = Map.empty[Long, (Cancellable, Cancellable, Map[ActorRef, Cancellable])]

  // maps from id to set of replicators
  var pendingReplicators = Map.empty[Long, Set[ActorRef]]

  var expectedSeq: Long = 0L

  def getLastSeq() = expectedSeq - 1

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
      println(s"I have ${replicators.size} replicators")

    case Insert(k, v, id) => update(k, Some(v), id)
    case Remove(k, id) => update(k, None, id)
    case Get(k, id) =>
      val value = if(kv contains k) Some(kv(k)) else None
      sender ! GetResult(k, value, id)

    case Persisted(_, id) =>
      updatePrimaryCompleteness(id)

      val (primaryCancellable, cancellableTimeLimit, cancellableReplicators) = pendingPersists(id)
      primaryCancellable.cancel()
      if(completed(id)) {
        cancellableTimeLimit.cancel()
        cancellableReplicators foreach { case (_, v) => v.cancel() }
        val requester = requesters(id)
        requesters = requesters - id
        pendingReplicators -= id
        requester ! OperationAck(id)
      }

    case Replicated(_, id) =>
      // TODO: control replication of new/deleted replicas

      val replicator = sender
      val updatedReplicators = pendingReplicators(id) - replicator
      if (updatedReplicators.isEmpty) {
        updateSecondariesCompleteness(id)
      }

      val (cancellable, cancellableTimeLimit, cancellableReplicators) = pendingPersists(id)
      pendingReplicators += (id -> (pendingReplicators(id) - replicator))
      if (completed(id)) {
        cancellable.cancel()
        cancellableTimeLimit.cancel()
        cancellableReplicators foreach { case (_, v) =>
          v.cancel()
        }
        val requester = requesters(id)
        requesters = requesters - id
        pendingReplicators -= id
        requester ! OperationAck(id)
      }
  }

  private def update(k: String, v: Option[String], id: Long) = {
    val requester = sender

    requesters += (id -> requester)
    if(v.isEmpty) kv -= k
    else kv += (k -> v.get)
    val persist = Persist(k, v, id)

    // primary persistence
    val cancellable = context.system.scheduler.schedule(0 milliseconds, 100 milliseconds)(persister ! persist)

    pendingReplicators += (id -> replicators)
    var mapOfCancellableReplicators = Map.empty[ActorRef, Cancellable]
    replicators foreach { r =>
      mapOfCancellableReplicators += (r -> context.system.scheduler.schedule(0 millisecond, 100 milliseconds)(r ! Replicate(k, v, id)))
    }

    val cancellableTimeLimit = context.system.scheduler.scheduleOnce(1 second) {
      cancellable.cancel()
      mapOfCancellableReplicators foreach { case(_, v) => v.cancel() }
      requester ! OperationFailed(id)
    }

    completeness += (id -> Tuple2(false, replicators.isEmpty))
    pendingPersists += (id -> Tuple3(cancellable, cancellableTimeLimit, mapOfCancellableReplicators))
  }

  private def updatePrimaryCompleteness(id: Long) = {
    val (_, secondaries) = completeness(id)
    completeness += (id -> Tuple2(true, secondaries))
  }

  private def updateSecondariesCompleteness(id: Long) = {
    val (primary, _) = completeness(id)
    completeness += (id -> Tuple2(primary, true))
  }

  private def completed(id: Long) = completeness(id)._1 && completeness(id)._2

  // TODO:
  private def createReplicator(replica: ActorRef) = {
    val newReplicator = context.actorOf(Replicator.props(replica))
    secondaries += (replica -> newReplicator)
    replicators += newReplicator
    kv foreach { case (k, v) => newReplicator ! Replicate(k, Some(v), -1) }
  }

  // TODO:
  private def deleteReplicator(delReplica: ActorRef) = {
    val deletedReplicator = secondaries(delReplica)
    secondaries -= delReplica
    replicators -= deletedReplicator
    context.stop(deletedReplicator)
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
        val persist = Persist(k, v, seq)
        requestersSnaps += (seq -> Tuple2(sender, persist))
        val cancellable = context.system.scheduler.schedule(0 seconds, 100 milliseconds)(persister ! persist)
        pendingPersists += (seq -> Tuple3(cancellable, null, null))
      }
    case Persisted(k, seq) =>
      val (cancellable, _, _) = pendingPersists(seq)
      cancellable.cancel()
      val (requester, _) = requestersSnaps(seq)
      requestersSnaps -= seq
      requester ! SnapshotAck(k, seq)
  }

}

