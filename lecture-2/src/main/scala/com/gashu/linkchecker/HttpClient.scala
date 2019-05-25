package com.gashu.linkchecker

import java.util.concurrent.{Executor, Executors}

import com.ning.http.client.AsyncHttpClient

import scala.concurrent._

/**
  * @author tiagogashu on 23/05/19
  **/
trait WebClient {
  def get(url: String): Future[String]
}

case class BadStatus(status: Int) extends RuntimeException

object HttpClient extends WebClient {

  val client = new AsyncHttpClient

  def get(url: String): Future[String] = {
    val f = client.prepareGet(url).execute()
    val p = Promise[String]()
    f.addListener(new Runnable {
      def run = {
        val response = f.get
        if(response.getStatusCode < 400)
          p.success(response.getResponseBodyExcerpt(131072))
        else p.failure(new RuntimeException(response.getStatusCode.toString))
      }
    }, Executors.newCachedThreadPool())
    p.future
  }

  def shutdown(): Unit = client.close()

}

object HttpClientMain extends App {
  import scala.concurrent.ExecutionContext.Implicits.global
  HttpClient get "http://www.google.com.br" map println andThen { case _ => HttpClient.shutdown() }


}
