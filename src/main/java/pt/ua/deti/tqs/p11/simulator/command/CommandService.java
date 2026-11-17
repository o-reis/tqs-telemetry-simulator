package pt.ua.deti.tqs.p11.simulator.command;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.Executor;
import pt.ua.deti.tqs.p11.simulator.engine.CommandOutcome;
import pt.ua.deti.tqs.p11.simulator.engine.SimulationEngine;

/**
 * Applies the platform's commands to the simulation and answers each one exactly once.
 *
 * <p>Commands arrive on the MQTT client's thread, but the engine is not thread-safe. So {@link
 * #submit} only notes when the command arrived and hands it to the simulation thread (the same
 * single-threaded executor that runs the ticks). Commands and ticks then run one after another,
 * never at the same time, and in the order they arrived.
 */
public class CommandService {
  private final SimulationEngine engine;
  private final ResponsePublisher responses;
  private final Clock clock;
  private final Executor simulationThread;

  /**
   * @param simulationThread the executor that also runs {@link SimulationEngine#tick()}
   */
  public CommandService(
      SimulationEngine engine,
      ResponsePublisher responses,
      Clock clock,
      Executor simulationThread) {
    this.engine = engine;
    this.responses = responses;
    this.clock = clock;
    this.simulationThread = simulationThread;
  }

  /** Accepts a command from any thread; it is applied and answered on the simulation thread. */
  public void submit(VehicleCommand command) {
    // The contract rejects a command *received* after expiresAt: the time is taken now, so a
    // busy simulation thread cannot make an on-time command expire.
    Instant receivedAt = clock.instant();
    simulationThread.execute(() -> responses.publish(command, apply(command, receivedAt)));
  }

  private CommandOutcome apply(VehicleCommand command, Instant receivedAt) {
    if (command.isExpiredAt(receivedAt)) {
      return CommandOutcome.rejected(CommandOutcome.EXPIRED);
    }
    return switch (command.name()) {
      case UNLOCK -> engine.unlock(command.vehicleId());
      case LOCK -> engine.lock(command.vehicleId());
      case PING -> engine.ping(command.vehicleId());
    };
  }
}
