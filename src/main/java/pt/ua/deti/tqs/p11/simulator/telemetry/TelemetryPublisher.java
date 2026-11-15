package pt.ua.deti.tqs.p11.simulator.telemetry;

import java.util.concurrent.CompletableFuture;

/**
 * Port through which the simulation sends vehicle telemetry out. The MQTT adapter implements it;
 * the simulation engine only knows this interface.
 */
public interface TelemetryPublisher {

  /**
   * Publishes the current state of a vehicle.
   *
   * @return completes when the broker accepted the message, or exceptionally if it failed
   */
  CompletableFuture<Void> publish(VehicleTelemetry telemetry);
}
