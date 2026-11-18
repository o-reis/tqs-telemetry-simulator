package pt.ua.deti.tqs.p11.simulator.event;

import java.util.concurrent.CompletableFuture;

/**
 * Port through which events go out. The MQTT adapter implements it; the simulation engine only
 * knows this interface.
 */
public interface EventPublisher {

  /**
   * Publishes something that happened once to a vehicle.
   *
   * @return completes when the broker accepted the message, or exceptionally if it failed
   */
  CompletableFuture<Void> publish(FaultEvent event);
}
