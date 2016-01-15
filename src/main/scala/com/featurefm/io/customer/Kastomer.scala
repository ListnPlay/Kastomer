package com.featurefm.io.customer

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.{Graph, FlowShape, Fusing}
import akka.stream.scaladsl._
import com.featurefm.io.HttpClient
import com.featurefm.metrics.{HealthCheck, HealthInfo, HealthState}
import com.typesafe.config.ConfigFactory
import org.json4s.JsonAST.{JObject, JString}
import org.reactivestreams.Processor

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
  * Created by yardena on 1/7/16.
  */
class Kastomer(implicit val system: ActorSystem) extends HealthCheck {

  private val config = ConfigFactory.load()
  private val log = Logging(system, getClass)

  private val siteId    = config.getString("keys.customer-io.site")
  private val apiSecret = config.getString("keys.customer-io.secret")

  private lazy val api = HttpClient.secure("track.customer.io")
  import api._

  private lazy val auth = Authorization(BasicHttpCredentials(siteId, apiSecret))

  val Flow = new Kastomer.Flows {
    def track = trackFlow
    def identify = identifyFlow
    def delete = deleteFlow
  }

  /**
    * Experimental
    */
  val Fuse = new Kastomer.Fused {
    def track = Fusing.aggressive(Flow.track)
    def identify = Fusing.aggressive(Flow.identify)
    def delete = Fusing.aggressive(Flow.delete)
  }

  val Processor = new Kastomer.Processors {
    def track: Processor[Event, Try[Int]] = Flow.track.toProcessor.run()
    def identify: Processor[User, Try[Int]] = Flow.identify.toProcessor.run()
    def delete: Processor[String, Try[Int]] = Flow.delete.toProcessor.run()
  }

  private def out = (t: Try[HttpResponse]) => t map (_.status.intValue())

  implicit val exec = system.dispatcher

  lazy val  trackFlow: Flow[Event, Try[Int], Any] = {
    val in = (e: Event) => Post(s"/api/v1/customers/${e.id}/events", e).addHeader(auth)

    BidiFlow.fromFunctions(in, out).joinMat(api.getTimedFlow("track"))(Keep.right)
  }

  lazy val  identifyFlow: Flow[User, Try[Int], Any] = {
    val in = (user: User) => Put(s"/api/v1/customers/${user.id}", user).addHeader(auth)

    BidiFlow.fromFunctions(in, out).joinMat(api.getTimedFlow("identify"))(Keep.right)
  }

  lazy val deleteFlow: Flow[String, Try[Int], Any] = {
    val in = (userId: String) => Delete(s"/api/v1/customers/$userId").addHeader(auth)

    BidiFlow.fromFunctions(in, out).joinMat(api.getTimedFlow("delete"))(Keep.right)
  }

  // ---------- health ---------

  override val healthCheckName: String = "customer-io"

  private[this] def responseFunction(t: Try[HttpResponse]): Future[HealthInfo] = t match {
    case Success(response) if response.status.isSuccess() =>
      Unmarshal(response.entity).to[JObject] map { body =>
        val JString(x) = body \ "meta" \ "message"
        new HealthInfo(HealthState.GOOD, details = x)
      } recover { case e =>
        new HealthInfo(HealthState.GOOD, details = s"Status: ${response.status}")
      }
    case Success(response) =>
      Unmarshal(response.entity).to[JObject] map { body =>
        new HealthInfo(HealthState.DEAD, details = s"Status: ${response.status}", extra = Some(body))
      } recover { case e =>
        new HealthInfo(HealthState.DEAD, details = s"Status: ${response.status}")
      }
    case Failure(e)        =>
      Future successful new HealthInfo(HealthState.DEAD, details = s"${e.toString}")
  }

  private lazy val healthFlow: Source[HealthInfo, _] =
    Source.single(Get("/auth").addHeader(auth)).via(api.getTimedFlow("auth")).mapAsync(1)(responseFunction)

  override def getHealth: Future[HealthInfo] = {
    healthFlow.runWith(Sink.head)
  }

}

object Kastomer {

  def apply()(implicit system: ActorSystem): Kastomer = new Kastomer()(system)

  trait Flows {
    def track: Flow[Event, Try[Int], Any]
    def identify: Flow[User, Try[Int], Any]
    def delete: Flow[String, Try[Int], Any]
  }
  trait Processors {
    def track: Processor[Event, Try[Int]]
    def identify: Processor[User, Try[Int]]
    def delete: Processor[String, Try[Int]]
  }
  /**
    * Experimental
    */
  trait Fused {
    def track: Graph[FlowShape[Event, Try[Int]], Any]
    def identify: Graph[FlowShape[User, Try[Int]], Any]
    def delete: Graph[FlowShape[String, Try[Int]], Any]
  }
}
