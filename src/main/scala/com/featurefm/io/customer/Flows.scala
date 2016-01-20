package com.featurefm.io.customer

import akka.stream.scaladsl.{Source, Flow}
import com.featurefm.metrics.HealthInfo

import scala.util.Try

/**
  * Created by yardena on 1/16/16.
  */
trait Flows {
  def track:          Flow[Event, (Try[Int], Event), Any]
  def trackSingle:    Flow[Event, Int,               Any]

  def identify:       Flow[User, (Try[Int], User),   Any]
  def identifySingle: Flow[User, Int,                Any]

  def delete:         Flow[String, Try[Int],         Any]
  def deleteSingle:   Flow[String, Int,              Any]

  def health:         Source[HealthInfo, Unit]
}
