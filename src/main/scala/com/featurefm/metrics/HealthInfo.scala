package com.featurefm.metrics

import com.featurefm.metrics.HealthState.HealthState

object HealthState extends Enumeration {
  type HealthState = Value
  val GOOD, SICK, DEAD = Value
}

case class HealthInfo(state: HealthState = HealthState.GOOD, details: String, extra: Option[AnyRef] = None,
                      checks: Seq[(String,HealthInfo)] = List.empty)
