package com.featurefm.io.customer

import akka.actor.ActorSystem
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Fusing
import akka.stream.scaladsl._
import com.featurefm.io.HttpClient
import com.featurefm.metrics.{HealthCheck, HealthInfo, HealthState}
import com.typesafe.config.ConfigFactory
import org.json4s.JsonAST.{JObject, JString}
import org.reactivestreams.Processor

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

/**
  * Created by yardena on 1/7/16.
  */
class Kastomer(implicit val system: ActorSystem) extends Flows with HealthCheck {

  private val config = ConfigFactory.load()
  private val siteId    = config.getString("keys.customer-io.site")
  private val apiSecret = config.getString("keys.customer-io.secret")
  private lazy val auth = Authorization(BasicHttpCredentials(siteId, apiSecret))


  private lazy val api = HttpClient.secure("track.customer.io")
  import api._

  private def out = (t: Try[HttpResponse]) => t map (_.status.intValue())

  import system.dispatcher

  override lazy val track: Flow[Event, Try[Int], Any] = {
    val in = (e: Event) => Post(s"/api/v1/customers/${e.id}/events", e).addHeader(auth)

    BidiFlow.fromFunctions(in, out).joinMat(api.getTimedFlow("track"))(Keep.right)
  }

  override lazy val identify: Flow[User, Try[Int], Any] = {
    val in = (user: User) => Put(s"/api/v1/customers/${user.id}", user).addHeader(auth)

    BidiFlow.fromFunctions(in, out).joinMat(api.getTimedFlow("identify"))(Keep.right)
  }

  override lazy val delete: Flow[String, Try[Int], Any] = {
    val in = (userId: String) => Delete(s"/api/v1/customers/$userId").addHeader(auth)

    BidiFlow.fromFunctions(in, out).joinMat(api.getTimedFlow("delete"))(Keep.right)
  }

  /**
    * Experimental
    */
  val Fuse = new Fused {
    def track = Fusing.aggressive(Kastomer.this.track)
    def identify = Fusing.aggressive(Kastomer.this.identify)
    def delete = Fusing.aggressive(Kastomer.this.delete)
  }

  val Processor = new Processors {
    def track: Processor[Event, Try[Int]] = Kastomer.this.track.toProcessor.run()
    def identify: Processor[User, Try[Int]] = Kastomer.this.identify.toProcessor.run()
    def delete: Processor[String, Try[Int]] = Kastomer.this.delete.toProcessor.run()
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

}
