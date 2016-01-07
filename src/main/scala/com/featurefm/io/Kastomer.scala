package com.featurefm.io

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.scaladsl.{BidiFlow, Flow, Keep}
import com.featurefm.metrics.{HealthCheck, HealthInfo, HealthState}
import com.typesafe.config.ConfigFactory
import org.json4s.JsonAST.{JObject, JString}
import org.reactivestreams.Processor

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

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

  def identify(user: User)(implicit ec: ExecutionContext = system.dispatcher): Future[Int] = {
    val uri = s"/api/v1/customers/${user.id}"
    val request = Put(uri, user).addHeader(auth)
    send(request, "identify") map toStatus
  }

  def track(event: Event)(implicit ec: ExecutionContext = system.dispatcher): Future[Int] = {
    val uri = s"/api/v1/customers/${event.id}/events"
    val request = Post(uri, event).addHeader(auth)
    send(request, "track") map toStatus
  }

  def delete(userId: String)(implicit ec: ExecutionContext = system.dispatcher): Future[Int] = {
    val uri = s"/api/v1/customers/$userId"
    val request = Delete(uri).addHeader(auth)
    send(request, "delete") map toStatus
  }

  private def toStatus(response: HttpResponse): Int = response.status.intValue()

  // ---------- health ---------

  override val healthCheckName: String = "customer-io"

  override def getHealth(implicit ec: ExecutionContext = system.dispatcher): Future[HealthInfo] = {
    send(Get("/auth").addHeader(auth), "auth") flatMap { response =>
      Unmarshal(response.entity).to[JObject] map { body =>
        if (response.status.isSuccess() ) {
          val JString(x) = body \ "meta" \ "message"
          new HealthInfo(HealthState.GOOD, details = x)
        } else if (response.status.intValue() / 100 == 4) {
          log.error(s"Customer.io returned code ${response.status}")
          throw new RuntimeException(s"Customer.io returned code ${response.status}")
        } else {
          new HealthInfo(HealthState.SICK, details = s"Status: ${response.status}", extra = Some(body))
        }
      }
    }
  }

  // ----------- streams ---------

  val Flow = new Kastomer.Flows {
    def track(implicit ec: ExecutionContext = system.dispatcher) = trackFlow(ec)
  }

  val Processor = new Kastomer.Processors {
    def track(implicit ec: ExecutionContext = system.dispatcher): Processor[Event, Try[Int]] = processor(ec)
  }

  private def trackFlow(implicit ec: ExecutionContext = system.dispatcher): Flow[Event, Try[Int], Any] = {

    val f = api.getTimedFlow("track")//Flow[HttpRequest, Try[HttpResponse], Http.HostConnectionPool]
    val in = (e: Event) => Post(s"/api/v1/customers/${e.id}/events", e).addHeader(auth)
    val out = (t: Try[HttpResponse]) => t map toStatus

    BidiFlow.fromFunctions(in, out).joinMat(f)(Keep.right)

  }

  private def processor(implicit ec: ExecutionContext = system.dispatcher): Processor[Event, Try[Int]] =
    trackFlow.toProcessor.run()

}

object Kastomer {
  trait Flows {
    def track(implicit ec: ExecutionContext): Flow[Event, Try[Int], Any]
  }
  trait Processors {
    def track(implicit ec: ExecutionContext): Processor[Event, Try[Int]]
  }
}
