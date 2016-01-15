package com.featurefm.io.customer

import java.util.UUID

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.testkit.{DefaultTimeout, ImplicitSender, TestKit}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}

import scala.util.{Failure, Success, Try}

/**
  * Created by yardena on 1/13/16.
  */
class KastomerSpec extends TestKit(ActorSystem("TestKit")) with DefaultTimeout
      with FlatSpecLike with Matchers with BeforeAndAfterAll with ScalaFutures {

  implicit val defaultPatience = PatienceConfig(timeout = Span(5, Seconds), interval = Span(500, Millis))

  val K = Kastomer()

  val userId = UUID.randomUUID().toString
  val events = Seq(
    Event(id = userId, "test_kastomer", Map("num" -> 1)),
    Event(id = userId, "test_kastomer", Map("num" -> 2)),
    Event(id = userId, "test_kastomer", Map("num" -> 3))
  )

  implicit val materializer = ActorMaterializer()

  "Kastomer" should "be able to track events" in {

    val user = new User(id = userId, "yardena@feature.fm", Map("got" -> 1, "obladi" -> "oblada"))
    val f = Source.single(user).via(K.identify).runWith(Sink.head)
    whenReady (f) {
      case Success(200) =>
        val track = K.track

        val f = Source.fromIterator[Event](() => events.iterator).
                via(track).
                runFold(List[Try[Int]]())(_ :+ _)

        whenReady(f) {
          case Success(200) :: Success(200) :: Success(200) :: Nil => println("Ok")
          case l => println(l); fail("Someting's wrong")
        }

      case Success(n) => fail(s"customer.io returned $n")
      case Failure(e) => fail(s"customer.io returned $e")
    }
  }

  it should "be able to track events with fusing" in {

    val user = new User(id = userId, "yardena@feature.fm", Map("got" -> 1, "obladi" -> "oblada"))
    val f = Source.single(user).via(K.Fuse.identify).runWith(Sink.head)
    whenReady (f) {
      case Success(200) =>
        val track = K.Fuse.track

        implicit val materializer = ActorMaterializer()

        val f = Source.fromIterator[Event](() => events.iterator).
                via(track).
                runFold(List[Try[Int]]())(_ :+ _)

        whenReady(f) {
          case Success(200) :: Success(200) :: Success(200) :: Nil => println("Ok")
          case l => println(l); fail("Someting's wrong")
        }

      case Success(n) => fail(s"customer.io returned $n")
      case Failure(e) => fail(s"customer.io returned $e")
    }
  }

  override protected def afterAll(): Unit = {
    system.terminate()
  }

}