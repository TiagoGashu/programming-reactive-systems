package async

import scala.concurrent.{Future, Promise}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}
import scala.util.control.NonFatal

object Async {

  /**
    * Transforms a successful asynchronous `Int` computation
    * into a `Boolean` indicating whether the number was even or not.
    * In case the given `Future` value failed, this method
    * should return a failed `Future` with the same error.
    */
  def transformSuccess(eventuallyX: Future[Int]): Future[Boolean] =
    eventuallyX flatMap(x => Future {
      x % 2 == 0
    })

  /**
    * Transforms a failed asynchronous `Int` computation into a
    * successful one returning `-1`.
    * Any non-fatal failure should be recovered.
    * In case the given `Future` value was successful, this method
    * should return a successful `Future` with the same value.
    */
  def recoverFailure(eventuallyX: Future[Int]): Future[Int] =
    eventuallyX.recover {
      case e: Throwable => -1
    }

  /**
    * Perform two asynchronous computation, one after the other. `makeAsyncComputation2`
    * should start ''after'' the `Future` returned by `makeAsyncComputation1` has
    * completed.
    * In case the first asynchronous computation failed, the second one should not even
    * be started.
    * The returned `Future` value should contain the successful result of the first and
    * second asynchronous computations, paired together.
    */
  def sequenceComputations[A, B](
    makeAsyncComputation1: () => Future[A],
    makeAsyncComputation2: () => Future[B]
  ): Future[(A, B)] =
    makeAsyncComputation1().flatMap {
      x1 => makeAsyncComputation2().map(x2 => (x1, x2))
    }

  /**
    * Concurrently perform two asynchronous computations and pair their successful
    * result together.
    * The two computations should be started independently of each other.
    * If one of them fails, this method should return the failure.
    */
  def concurrentComputations[A, B](
    makeAsyncComputation1: () => Future[A],
    makeAsyncComputation2: () => Future[B]
  ): Future[(A, B)] =
    makeAsyncComputation1() zip makeAsyncComputation2()

  /**
    * Attempt to perform an asynchronous computation.
    * In case of failure this method should try again to make
    * the asynchronous computation so that at most `maxAttempts`
    * are eventually performed.
    */
  def insist[A](makeAsyncComputation: () => Future[A], maxAttempts: Int): Future[A] = {
    def retry(f: () => Future[A], maxAttempts: Int, attempt: Int): Future[A] = {
      if(maxAttempts == attempt) Future.failed(new RuntimeException("Exceeded max attempts"))
      else f() recoverWith { case e: Throwable => retry(f, maxAttempts, attempt + 1) }
    }
    retry(makeAsyncComputation, maxAttempts, 0)
  }

  /**
    * Dummy example of a callback-based API
    */
  trait CallbackBasedApi {
    def computeIntAsync(continuation: Try[Int] => Unit): Unit
  }

  /**
    * API similar to [[CallbackBasedApi]], but based on `Future` instead
    */
  trait FutureBasedApi {
    def computeIntAsync(): Future[Int]
  }

  /**
    * Turns a callback-based API into a Future-based API
    * @return A `FutureBasedApi` that forwards calls to `computeIntAsync` to the `callbackBasedApi`
    *         and returns its result in a `Future` value
    */
    def futurize(callbackBasedApi: CallbackBasedApi): FutureBasedApi = {
      var value: Int = 0
      val eventuallyInteger = Future[Int] {
        callbackBasedApi.computeIntAsync(x => {
          value = x.get
        })
        value
      }
      () => eventuallyInteger
  }

}
