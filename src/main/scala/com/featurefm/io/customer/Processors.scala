package com.featurefm.io.customer

import org.reactivestreams.Processor

import scala.util.Try

/**
  * Created by yardena on 1/16/16.
  */
trait Processors {
  def track:    Processor[Event, (Try[Int], Event)]
  def identify: Processor[User,  (Try[Int], User)]
  def delete:   Processor[String, Try[Int]]
}

