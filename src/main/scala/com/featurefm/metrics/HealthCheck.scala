package com.featurefm.metrics

import scala.concurrent.Future

trait HealthCheck {

  val healthCheckName = getClass.getSimpleName

  def getHealth: Future[HealthInfo]
}
