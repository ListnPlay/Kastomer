# Kastomer #
This is a [Reactive-streams](http://www.reactive-streams.org/) REST client for [Customer.io API](https://customer.io/docs/api/rest.html) implemented [with Akka-Http](http://doc.akka.io/docs/akka-stream-and-http-experimental/snapshot/scala.html)

If you are looking for an alternative for Scala - see [Dispatch](http://dispatch.databinder.net/Dispatch.html)-based [client](https://github.com/learndot/customer-io-scala).

## User Guide ##
### sbt ###
To include it in your project add
```
"com.featurefm" %% "kastomer" % "0.0.2"
```
Only Scala 2.11 is currently supported.

### Code ###
Customer.io API provide 3 end-points: identify, track and delete. This library gives you 3 corresponding flows.

     trait Flows {
       def health:   Source[HealthInfo, Unit]
       def identify: Flow[User,  (Try[Int], User),  Any]
       def track:    Flow[Event, (Try[Int], Event), Any]
       def delete:   Flow[String, Try[Int],         Any]
     }

The main implementation is `Kastomer` class, you can create an instance with `Kastomer()`, providing that you have an implicit ActorSystem in scope. To create one:

     implicit val system = ActorSystem("My-App")


### Checking connection ###

To run the client you need to provide `CUSTOMERIO_SITEID` and `CUSTOMERIO_APIKEY` environment variables.

Use `health.runWith(Sink.head)` to test connection, it will produce `Future[HealthInfo]` and if you provided good credentials you will get `HealthInfo(HealthState.GOOD, "nice credentials")`

### Before you start - general notes about the API ###

`Try[Int]` represents the HTTP response status after calling customer.io API, and it should be Success(200) if all went well. If Failure is returned it probably signals either connection problem or bug in the code. Non-200 return code will indicate problem on customer.io side or invalid credentials.

The flow can be used to send a single request to customer.io, or it can be used to stream requests from some source to customer.io, for example results of a database query, a file read, or just an in-memory list of elements. When streaming multiple elements, it may be useful to correlate the responses (especially failures) with the element which processing produced them, for example to report or to retry the request. For that purpose the library provides a tuple in response to **track** and **identify**, first element of the tuple is the response, and the second is the element which processing produced the result.

If you are new to akka-streams: to build any stream you need an implicit `Materializer` value in scope, it s usually created like this:

     implicit val materializer = ActorMaterializer()

that in turn assumes an implicit ActorSystem.

### Identifying user ###

We provide a simple `User` class:

    case class User(id: String, email: String, data: Any)

The `data` field should be of a type serializable to json, e.g. a case class or a Map (of serializable values). See _Implementation_ section below for more details.

In a general case to identify request you can use code like:

    val source: Source[User, _] = ??? //some source, e.g. Source.single(user)
    val sink = Sink.head[(Try[Int], User)] //you can of course use any other sink of your choosing
    val f: Future[(Try[Int], User)] = source.via(identify).runWith()

But for a single request we recommend using a convenience `identifySingle` method:

    val f: Future[Int] = Source.single(user).via(identifySingle).runWith(Sink.head)

### Tracking events ###

We provide a simple `Event` class:

    case class Event(id: String, name: String, data: Any)

The `data` field should be of a type serializable to json, e.g. a case class or a Map (of serializable values). See _Implementation_ section below for more details.

In a general case to track a request you can use code like:

    val source: Source[Event, _] = ??? //some source, e.g. Source.single(user)
    val sink: Sink[(Try[Int], Event), _] = ??? //some sink of your choosing
    val x = source.via(track).runWith(sink)

As with identifying users, in case of a single request, you can use convenience `trackSingle` method:

    val f: Future[Int] = Source.single(event).via(trackSingle).runWith(Sink.head)

### Delete user ###

The `delete` flow takes user id as parameter. It can be used in the same way as `identify` and `track` above, but it does not return additional information with failure, as there's not much that can be done in such case. It has `deleteSingle` shortcut method, like the other flows.

### Configuring HTTP ###

Under the hood, this library uses [Akka-Http](http://doc.akka.io/docs/akka-stream-and-http-experimental/snapshot/scala.html) and Akka-Streams. Specifically it is based on [Host-Level Client Api](http://doc.akka.io/docs/akka-stream-and-http-experimental/snapshot/scala/http/client-side/host-level.html#host-level-api). There are several parameters that can be configured for the connection pool, see **reference.conf** file `http.host-connection-pool` section for their list.

### JSON ###
The library uses [json4s with jackson](https://github.com/json4s/json4s#jackson) via the [akka-http-json](https://github.com/hseeberger/akka-http-json) to serialize the requests. In addition to default serializers, [Joda-Time](http://www.joda.org/joda-time/) and UUID are supported.

### Metrics and Health ###

The library registers Health and Metrics extensions that enable Akka integration with [Dropwizard (formerly known as CodaHale) Metrics](http://metrics.dropwizard.io/).

The library automatically registers a timer with each type of flows. If you wish to access the [timers](https://github.com/erikvanoosten/metrics-scala/blob/master/src/main/scala/nl/grons/metrics/scala/Timer.scala) in your code, you can get them from Kastomer instance Timer property that returns

```scala
import nl.grons.metrics.scala.{Timer => ScalaTimer}

trait Timers {
  def health:   ScalaTimer
  def identify: ScalaTimer
  def track:    ScalaTimer
  def delete:   ScalaTimer
}
```

For example: `K.Timer.track.count` or `K.Timer.track.mean`

To register itself as a HealthCheck, your application needs to call `Health().addCheck` passing the Kastomer instance.

## Implementation ##

`com.featurefm.io.HttpClient` class provides most of the heavy lifting here. This class is part of our ([feature.fm](http://www.feature.fm/)) [micro-service infrastructure](https://github.com/ListnPlay/RiverSong) that provides both server and client implementation based on Akka-Http. We use the client to connect to many services, both internal and external, not just customer.io.

## Reactive-Streams ##

Since Akka-Streams provide integration with [Reactive-streams](http://www.reactive-streams.org/), this library also provides `Processor`s that correspond to **identify**, **track** and **delete** flows.

## Advanced Example ##
Akka-Http Site client will retry idempotent requests (according to configuration). Since **identify** request is a `PUT`, it is considered idempotent. But **track** is a `POST`, therefore if we want to retry it, we need to do it ourselves.

```scala
import com.featurefm.io.customer._
import akka.stream.scaladsl._
import scala.util._
import scala.concurrent.Future
import java.util.concurrent.atomic.AtomicLong

val K = Kastomer()
val track: Flow[Event, (Try[Int], Event), _] = K.track
val failed: Flow[(Try[Int],Event), Event, _] =
  Flow[(Try[Int],Event)].filter(_._1.filter(_ == 200).isFailure).map(_._2)

val successes = new AtomicLong()
val countSuccess =
    Sink.foreach[(Try[Int], Event)] {
      case (Success(200), _)  => successes.incrementAndGet()
      case _ => //do nothing
    }
val report: Sink[(Try[Int], Event), Future[List[Event]]] =
    Sink.fold[List[Event], (Try[Int], Event)](List[Event]()) {
      case (res, (Success(200), event))  =>
        successes.incrementAndGet()
        res
      case (res, (Success(code), event)) =>
        res :+ event
      case (res, (Failure(e),    event)) =>
        res :+ event
    }
val source: Source[Event, _ ] = ???

(source via track alsoTo countSuccess
      via failed via track //retry failed
      ).runWith(report)
```
