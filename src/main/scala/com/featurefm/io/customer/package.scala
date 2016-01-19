package com.featurefm.io

import akka.http.scaladsl.model.{HttpResponse, HttpRequest}
import akka.stream.scaladsl.Flow

import scala.util.Try

/**
  * Created by yardena on 1/19/16.
  */
package object customer {

  type Context = Map[String, Any]
  type RequestInContext  = InContext[HttpRequest]
  type ResponseInContext = InContext[Try[HttpResponse]]
  type FlowType = Flow[RequestInContext, ResponseInContext, Any]

}
