package com.featurefm.io.customer

import nl.grons.metrics.scala.{Timer => ScalaTimer}

/**
  * Created by yardena on 1/21/16.
  */
trait Timers {
  def identify: ScalaTimer
  def track:    ScalaTimer
  def delete:   ScalaTimer
  def health:   ScalaTimer
}
