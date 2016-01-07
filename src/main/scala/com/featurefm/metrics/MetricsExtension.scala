package com.featurefm.metrics

import akka.actor._
import com.codahale.metrics.MetricRegistry

class MetricsExtension(extendedSystem: ExtendedActorSystem) extends Extension {

  // Allow access to the extended system
  val system = extendedSystem
  // The application wide metrics registry.
  val metricRegistry = new MetricRegistry()

}

object Metrics extends ExtensionId[MetricsExtension] with ExtensionIdProvider {

  //The lookup method is required by ExtensionIdProvider,
  // so we return ourselves here, this allows us
  // to configure our extension to be loaded when
  // the ActorSystem starts up
  override def lookup() = Metrics

  //This method will be called by Akka
  // to instantiate our Extension
  override def createExtension(system: ExtendedActorSystem) = new MetricsExtension(system)

  def apply()(implicit system: ActorSystem): MetricsExtension = system.registerExtension(this)

}