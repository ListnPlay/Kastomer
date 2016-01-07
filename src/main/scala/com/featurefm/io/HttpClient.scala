package com.featurefm.io

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import com.codahale.metrics.Timer
import com.featurefm.metrics.Instrumented
import nl.grons.metrics.scala.MetricName

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

/**
 * Created by yardena on 1/4/16.
 */
final class HttpClient private (secure: Boolean = false, host: String, port: Int)(implicit val system: ActorSystem) extends Json4sProtocol with Instrumented {

  private lazy val name: String = s"$host:$port"
  override lazy val metricBaseName: MetricName = MetricName(this.getClass.getSimpleName, name)

  implicit val materializer = ActorMaterializer()

  private val httpFlow = if (secure) Http().cachedHostConnectionPoolTls[Timer.Context](host, port)
                    else Http().cachedHostConnectionPool[Timer.Context](host, port)

  val flows = TrieMap[String, Flow[HttpRequest, Try[HttpResponse], Http.HostConnectionPool]]()

  def getTimedFlow(name: String): Flow[HttpRequest, Try[HttpResponse], Http.HostConnectionPool] =
    flows.getOrElseUpdate(name, makeTimedFlow(name))

  def makeTimedFlow(name: String): Flow[HttpRequest, Try[HttpResponse], Http.HostConnectionPool] = {
    val req = (r: HttpRequest) => (r, metrics.timer(name).timerContext())
    val res = (t: (Try[HttpResponse], Timer.Context)) => { t._2.stop(); t._1 }

    BidiFlow.fromFunctions(req, res).joinMat(httpFlow)(Keep.right)
  }

  def send(request: HttpRequest, requestName: String)(implicit ec: ExecutionContext = system.dispatcher): Future[HttpResponse] = {
    val naming = MetricImplicits.FixedNaming(requestName)
    Source.single(request).via(getTimedFlow(naming(request))).runWith(Sink.head).map(_.get)
  }

}

object HttpClient extends MetricImplicits {

  def http(host: String, port: Int = 80)(implicit system: ActorSystem) = new HttpClient(secure = false, host, port)
  def https(host: String, port: Int = 443)(implicit system: ActorSystem) = new HttpClient(secure = true, host, port)

  def apply (host: String, port: Int = 80 )(implicit system: ActorSystem): HttpClient = http(host, port)
  def secure(host: String, port: Int = 443)(implicit system: ActorSystem): HttpClient = https(host, port)

}
