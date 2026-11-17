package pt.ua.deti.tqs.p11.simulator.command;

import java.util.concurrent.CompletableFuture;
import pt.ua.deti.tqs.p11.simulator.engine.CommandOutcome;

/**
 * Port through which the answer to a command goes out. The MQTT adapter implements it; the command
 * service only knows this interface.
 */
public interface ResponsePublisher {

  /**
   * Sends the response to a command.
   *
   * @return completes when the broker accepted the message, or exceptionally if it failed
   */
  CompletableFuture<Void> publish(VehicleCommand command, CommandOutcome outcome);
}
