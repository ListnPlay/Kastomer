package com.featurefm.io.customer

import akka.stream.{FlowShape, Graph}

import scala.util.Try

/**
  * Created by yardena on 1/16/16.
  *
  * Experimental
  */
trait Fused {
  def track:    Graph[FlowShape[Event,  (Try[Int], Event)], Any]
  def identify: Graph[FlowShape[User,   (Try[Int], User)],  Any]
  def delete:   Graph[FlowShape[String, Try[Int]],          Any]
}
