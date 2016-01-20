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

  private def responseStatus(t: Try[HttpResponse]): Try[Int] = t map (_.status.intValue())
  private val toStatus: ResponseInContext => Try[Int] = responseStatus(_)

  /**
    * Takes an event, sends it to customer.io and return the status code of the response
    *
    * Returns not just result Try[Int], but also the event it corresponds to. This allows
    * to handle failures - report them or retry by feeding the event back to the flow
    */
  override lazy val track: Flow[Event, (Try[Int], Event), Any] = {
    val toRequest: (Event) => RequestInContext = e =>
      Post(s"/api/v1/customers/${e.id}/events", e).addHeader(auth) ->
        Map("event" -> e)

    val fromResponse: (ResponseInContext) => (Try[Int], Event) = r =>
      responseStatus(r) -> r.get[Event]("event")

    Flow[Event].map(toRequest).via(api.getTimedFlow("track")).map(fromResponse)
  }


  override lazy val trackSingle: Flow[Event, Try[Int], Any] = track.map(_._1)

  /**
    * Takes an user, sends it to customer.io and return the status code of the response*
    */
  override lazy val identify: Flow[User, (Try[Int], User), Any] = {
    val toRequest: (User) => RequestInContext = user =>
      Put(s"/api/v1/customers/${user.id}", user).addHeader(auth) ->
        Map("user" -> user)

    val fromResponse: (ResponseInContext) => (Try[Int], User) = r =>
      responseStatus(r) -> r.get[User]("user")

    Flow[User].map(toRequest).via(api.getTimedFlow("identify")).map(fromResponse)
  }

  override lazy val identifySingle: Flow[User, Try[Int], Any] = identify.map(_._1)

  /**
    * Takes a user id, sends it to customer.io and return the status code of the response*
    */
  override lazy val delete: Flow[String, Try[Int], Any] = {
    val toRequest: (String) => RequestInContext = userId => Delete(s"/api/v1/customers/$userId").addHeader(auth)

    Flow[String].map(toRequest).via(api.getTimedFlow("delete")).map(toStatus)
  }

  /**
    * Experimental
    */
  val Fuse = new Fused {
    def track     = Fusing.aggressive(Kastomer.this.track)
    def identify  = Fusing.aggressive(Kastomer.this.identify)
    def delete    = Fusing.aggressive(Kastomer.this.delete)
  }

  /**
    * Can be used from any reactive-streams compatible client, including Java
    */
  val Processor = new Processors {
    def track     = Kastomer.this.track.toProcessor.run()
    def identify  = Kastomer.this.identify.toProcessor.run()
    def delete    = Kastomer.this.delete.toProcessor.run()
  }

  // ---------- health ---------

  override val healthCheckName: String = "customer-io"

  private[this] def parseResponse(t: ResponseInContext): Future[HealthInfo] = t.unwrap match {
    case Success(response) if response.status.isSuccess() =>
      Unmarshal(response.entity).to[JObject] map { body =>
        val JString(x) = body \ "meta" \ "message"
        new HealthInfo(HealthState.GOOD, details = x)
      }
    case Success(response) =>
      Unmarshal(response.entity).to[JObject] map { body =>
        new HealthInfo(HealthState.DEAD, details = s"Status: ${response.status}", extra = Some(body))
      }
    case Failure(e)        =>
      Future successful new HealthInfo(HealthState.DEAD, details = s"${e.toString}")
  }

  lazy val healthFlow: RunnableGraph[Future[HealthInfo]] =
    Source.single[RequestInContext](Get("/auth").addHeader(auth)).
      via(api.getTimedFlow("auth")).mapAsync(1)(parseResponse).
      toMat(Sink.head)(Keep.right)

  override def getHealth: Future[HealthInfo] = {
    healthFlow.run() //.runWith(Sink.head)
  }

}

object Kastomer {

  def apply()(implicit system: ActorSystem): Kastomer = new Kastomer()(system)

}
