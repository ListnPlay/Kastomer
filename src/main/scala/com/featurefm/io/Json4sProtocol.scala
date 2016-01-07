package com.featurefm.io

import com.featurefm.metrics.HealthState
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import org.json4s._
import org.json4s.ext.{UUIDSerializer, JodaTimeSerializers, EnumNameSerializer}

/**
 * Created by yardena on 9/20/15.
 */
trait Json4sProtocol extends Json4sSupport {
  implicit val serialization = jackson.Serialization
  implicit val json4sJacksonFormats: Formats = DefaultFormats ++
    JodaTimeSerializers.all + UUIDSerializer + new EnumNameSerializer(HealthState)
}

object Json4sProtocol extends Json4sProtocol