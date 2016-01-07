package com.featurefm.io

import akka.http.scaladsl.marshalling.ToEntityMarshaller

/**
  * Created by yardena on 1/7/16.
  */
case class Event[E: ToEntityMarshaller](id: String, name: String, data: E)