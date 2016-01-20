package com.featurefm.io.customer

import akka.stream.scaladsl.Flow

import scala.util.Try

/**
  * Created by yardena on 1/16/16.
  */
trait Flows {
  def track:          Flow[Event, (Try[Int], Event), Any]
  def trackSingle:    Flow[Event, Try[Int],          Any]

  def identify:       Flow[User, (Try[Int], User),   Any]
  def identifySingle: Flow[User, Try[Int],           Any]

  def delete:         Flow[String, Try[Int],         Any]
}
