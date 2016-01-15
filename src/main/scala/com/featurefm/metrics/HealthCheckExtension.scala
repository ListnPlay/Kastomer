package com.featurefm.metrics

import akka.actor._
import com.codahale.metrics.health.HealthCheckRegistry
import nl.grons.metrics.scala.{CheckedBuilder, MetricName}

import scala.collection.concurrent.TrieMap

class HealthCheckExtension(system: ExtendedActorSystem) extends Extension {

  /** The application wide registry. */
  private val registry: collection.concurrent.Map[String,HealthCheck] = TrieMap()


  val codaRegistry = new com.codahale.metrics.health.HealthCheckRegistry()

  lazy val codaBridge = new CheckedBuilder {
    override lazy val metricBaseName: MetricName = MetricName(system.name)
    override val registry: HealthCheckRegistry = codaRegistry
  }

  /**
   * Get a copy of the registered `HealthCheck` definitions
   * @return
   */
  def getChecks: Seq[HealthCheck] = registry.values.toList

  /**
   * Add a health check to the registry
   * @param check
   */
  def addCheck(check: HealthCheck): Unit = {
    registry.putIfAbsent(check.healthCheckName ,check)
    import system.dispatcher
    codaBridge.healthCheck(check.healthCheckName) {
      check.getHealth map { h =>
        if (h.state != HealthState.GOOD) throw new RuntimeException(h.details)
      }
    }
  }

  def removeCheck(check: HealthCheck) = {
    registry.remove(check.healthCheckName, check)
  }

}

object Health extends ExtensionId[HealthCheckExtension] with ExtensionIdProvider {

  //The lookup method is required by ExtensionIdProvider,
  // so we return ourselves here, this allows us
  // to configure our extension to be loaded when
  // the ActorSystem starts up
  override def lookup() = Health

  //This method will be called by Akka
  // to instantiate our Extension
  override def createExtension(system: ExtendedActorSystem) = new HealthCheckExtension(system)

  def apply()(implicit system: ActorSystem): HealthCheckExtension =
    system.registerExtension(this)
}
