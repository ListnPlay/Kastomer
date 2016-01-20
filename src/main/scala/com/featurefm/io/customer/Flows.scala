package com.featurefm.io.customer

import akka.stream.scaladsl.Flow

import scala.util.Try

/**
  * Created by yardena on 1/16/16.
  */
trait Flows {
  def track: Flow[Event, (Event,Try[Int]), Any]
  def identify: Flow[User, (User, Try[Int]), Any]
  def delete: Flow[String, Try[Int], Any]
}
