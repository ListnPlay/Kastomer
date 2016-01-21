package com.featurefm.io

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import com.codahale.metrics.Timer
import nl.grons.metrics.scala.{Timer => ScalaTimer}
import com.featurefm.metrics.Instrumented
import nl.grons.metrics.scala.MetricName

import scala.collection.concurrent.TrieMap

/**
 * Created by yardena on 1/4/16.
 */
final class HttpClient private (secure: Boolean = false, host: String, port: Int)(implicit val system: ActorSystem) extends Json4sProtocol with Instrumented {

  private lazy val name: String = s"$host:$port"
  override lazy val metricBaseName: MetricName = MetricName(this.getClass.getSimpleName, name)

  implicit val materializer = ActorMaterializer()
  implicit val executor = system.dispatcher

  private val httpFlow = if (secure) Http().cachedHostConnectionPoolTls[Context](host, port)
                                else Http().cachedHostConnectionPool   [Context](host, port)

  private val flows = TrieMap[String, FlowType]()

  def getTimedFlow(name: String): FlowType = flows.getOrElseUpdate(name, makeTimedFlow(name))

  def makeTimedFlow(name: String): FlowType = {

    def attachTimerToRequest(x: RequestInContext): RequestInContext#Tuple =
      x.with_("timer", metrics.timer(name).timerContext()).toTuple

    def stopTimerReturnRequest(x: ResponseInContext#Tuple): ResponseInContext = {
      val y: ResponseInContext = InContext.fromTuple(x)
      y.get[Timer.Context]("timer").stop()
      y //y.without("timer")
    }

    Flow[InContext[HttpRequest]].map(attachTimerToRequest).via(httpFlow).map(stopTimerReturnRequest)
  }

  def getTimer(name: String): ScalaTimer = metrics.timer(name)

}

object HttpClient {

  def http(host: String, port: Int = 80)(implicit system: ActorSystem) = new HttpClient(secure = false, host, port)
  def https(host: String, port: Int = 443)(implicit system: ActorSystem) = new HttpClient(secure = true, host, port)

  def apply (host: String, port: Int = 80 )(implicit system: ActorSystem): HttpClient = http(host, port)
  def secure(host: String, port: Int = 443)(implicit system: ActorSystem): HttpClient = https(host, port)

}
