package com.featurefm.metrics

import scala.concurrent.{ExecutionContext, Future}

trait HealthCheck {

  val healthCheckName = getClass.getSimpleName

  def getHealth: Future[HealthInfo]
}
