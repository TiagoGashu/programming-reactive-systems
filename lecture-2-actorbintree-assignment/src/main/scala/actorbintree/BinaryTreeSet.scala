/**
 * Copyright (C) 2009-2013 Typesafe Inc. <http://www.typesafe.com>
 */
package actorbintree

import actorbintree.BinaryTreeSet.{Contains, ContainsResult, Insert, OperationFinished, Remove}
import akka.actor._

import scala.collection.immutable.Queue
import scala.collection.mutable

object BinaryTreeSet {

  trait Operation {
    def requester: ActorRef
    def id: Int
    def elem: Int
  }

  trait OperationReply {
    def id: Int
  }

  /** Request with identifier `id` to insert an element `elem` into the tree.
    * The actor at reference `requester` should be notified when this operation
    * is completed.
    */
  case class Insert(requester: ActorRef, id: Int, elem: Int) extends Operation

  /** Request with identifier `id` to check whether an element `elem` is present
    * in the tree. The actor at reference `requester` should be notified when
    * this operation is completed.
    */
  case class Contains(requester: ActorRef, id: Int, elem: Int) extends Operation

  /** Request with identifier `id` to remove the element `elem` from the tree.
    * The actor at reference `requester` should be notified when this operation
    * is completed.
    */
  case class Remove(requester: ActorRef, id: Int, elem: Int) extends Operation

  /** Request to perform garbage collection*/
  case object GC

  /** Holds the answer to the Contains request with identifier `id`.
    * `result` is true if and only if the element is present in the tree.
    */
  case class ContainsResult(id: Int, result: Boolean) extends OperationReply
  
  /** Message to signal successful completion of an insert or remove operation. */
  case class OperationFinished(id: Int) extends OperationReply

}

class BinaryTreeSet extends Actor {
  import BinaryTreeSet._
  import BinaryTreeNode._

  def createRoot: ActorRef = context.actorOf(BinaryTreeNode.props(0, initiallyRemoved = true))

  var root = createRoot

  // optional
  var pendingQueue = Queue.empty[Operation]

  // optional
  def receive: Receive = waiting

  // optional
  /** Accepts `Operation` and `GC` messages. */
  val waiting: Receive = {
    case Insert(requester: ActorRef, id: Int, elem: Int) =>
      root ! Insert(requester, id, elem)
    case Remove(requester: ActorRef, id: Int, elem: Int) =>
      root ! Remove(requester, id, elem)
    case Contains(requester: ActorRef, id: Int, elem: Int) =>
      root ! Contains(requester, id, elem)
    case GC =>
      val newRoot = createRoot
      root ! CopyTo(newRoot)
      context.become(garbageCollecting(newRoot))
  }

  // optional
  /** Handles messages while garbage collection is performed.
    * `newRoot` is the root of the new binary tree where we want to copy
    * all non-removed elements into.
    */
  def garbageCollecting(newRoot: ActorRef): Receive = {
    case CopyFinished =>
      root = newRoot
      context.become(waiting)
  }

}

object BinaryTreeNode {
  trait Position

  case object Left extends Position
  case object Right extends Position

  case class CopyTo(treeNode: ActorRef)
  case object CopyFinished

  def props(elem: Int, initiallyRemoved: Boolean) = Props(classOf[BinaryTreeNode],  elem, initiallyRemoved)
}

class BinaryTreeNode(val elem: Int, initiallyRemoved: Boolean) extends Actor {
  import BinaryTreeNode._
  import BinaryTreeSet._

  val positions = Array(Left, Right)

  var subtrees = mutable.Map[Position, ActorRef]()
  var removed = initiallyRemoved

  // optional
  def receive = waiting

  // optional
  /** Handles `Operation` messages and `CopyTo` requests. */
  val waiting: Receive = {
    case Insert(requester: ActorRef, id: Int, elem: Int) => {
      if(this.elem == elem) {
        println("Found an element already inserted")
        removed = false
        requester ! OperationFinished(id)
      } else {

        val pos = if (this.elem > elem) Left else Right
        if (subtrees.contains(pos)) {
          println(s"recursive call to $pos")
          subtrees(pos) ! Insert(requester, id, elem)
        }
        else {
          println(s"${this.elem}: inserting a node at $pos with $elem")
          subtrees = subtrees + (pos -> context.actorOf(props(elem, initiallyRemoved = false)))
          requester ! OperationFinished(id)
        }
      }
    }
    case Remove(requester: ActorRef, id: Int, elem: Int) => {
      if(this.elem == elem) {
        removed = true
        requester ! OperationFinished(id)
      } else {

        val pos = if (this.elem > elem) Left else Right
        if (subtrees.contains(pos)) {
          subtrees(pos) ! Remove(requester, id, elem)
        } else {
          requester ! OperationFinished(id)
        }
      }
    }
    case Contains(requester: ActorRef, id: Int, elem: Int) => {
      if (this.elem == elem) {
        requester ! ContainsResult(id, result = !this.removed)
      } else {

        val pos = if (this.elem > elem) Left else Right
        if (subtrees.contains(pos)) subtrees(pos) ! Contains(requester, id, elem)
        else requester ! ContainsResult(id, result = false)
      }
    }
    case CopyTo(newRoot: ActorRef) =>
      var expectedChildren: Set[ActorRef] = Set()
      if(!this.removed) {
        newRoot ! Insert(this.self, 1, this.elem)
        expectedChildren = expectedChildren + this.self
      }
      positions.foreach(p => {
        if(subtrees contains p) {
          expectedChildren = expectedChildren + subtrees(p)
          subtrees(p) ! CopyTo(newRoot)
        }
      })
      val client = sender
      context.become(copying(client, expectedChildren, insertConfirmed = false))
  }

  // optional
  /** `expected` is the set of ActorRefs whose replies we are waiting for,
    * `insertConfirmed` tracks whether the copy of this node to the new tree has been confirmed.
    */
  def copying(client: ActorRef, expected: Set[ActorRef], insertConfirmed: Boolean): Receive = {
    // finished the insert in new root
    case OperationFinished => {
      if(copyingFinished(expected, this.self)) {
        client ! CopyFinished
        context.become(waiting)
      }
      else context.become(copying(client, expected - this.self, insertConfirmed = true))
    }
    case CopyFinished => {
      if(copyingFinished(expected, sender)) {
        client ! CopyFinished
        context.become(waiting)
      }
      else context.become(copying(client, expected - sender, insertConfirmed))
    }
  }

  def copyingFinished(e: Set[ActorRef], node: ActorRef): Boolean = (e contains node) && (e.size == 1)

}

object Requester {
  case class Request(id: Int, elem: Int)
}

// actor for testing purposes
class Requester extends Actor {

  val bts = context.actorOf(Props[BinaryTreeSet], "bts")

  val ops = List(
    Insert(this.self, id = 100, 1),
    Contains(this.self, id = 50, 2),
    Remove(this.self, id = 10, 1),
    Insert(this.self, id = 20, 2),
    Contains(this.self, id = 80, 1),
    Contains(this.self, id = 70, 2)
  )

  ops foreach { op =>
    bts ! op
  }

  def receive = {
    case OperationFinished(id) =>
      println(s"$id")
    case ContainsResult(id, result) =>
      println(s"$id: $result")
  }

}

object BinaryTreeSetMain extends App {
  val system: ActorSystem = ActorSystem("akka-system")

  val requester = system.actorOf(Props[Requester], "requester")

}
