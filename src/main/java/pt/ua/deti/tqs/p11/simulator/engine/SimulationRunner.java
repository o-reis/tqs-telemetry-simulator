package pt.ua.deti.tqs.p11.simulator.engine;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/**
 * Runs the simulation in real time: calls the engine's tick at a fixed rate, from when the
 * application starts until it stops. It is the only engine class that depends on real time, so the
 * engine itself is tested without waiting.
 */
public class SimulationRunner implements SmartLifecycle {

  private static final Logger LOG = LoggerFactory.getLogger(SimulationRunner.class);
  private static final double NANOS_PER_SECOND = 1_000_000_000d;

  private final Runnable tick;
  private final Duration interval;
  private final ScheduledExecutorService scheduler;
  private ScheduledFuture<?> scheduled;

  /**
   * @param tick what to run on every interval, normally {@link SimulationEngine#tick()}
   * @param interval time between two ticks
   */
  public SimulationRunner(Runnable tick, Duration interval, ScheduledExecutorService scheduler) {
    this.tick = tick;
    this.interval = interval;
    this.scheduler = scheduler;
  }

  /** Converts the scenario's {@code emissionIntervalSeconds} to a duration. */
  static Duration intervalOf(double seconds) {
    return Duration.ofNanos(Math.round(seconds * NANOS_PER_SECOND));
  }

  @Override
  public synchronized void start() {
    if (scheduled != null) {
      return;
    }
    // Fixed rate: ticks are spaced by the schedule, not by the end of the previous tick, so the
    // rate does not drift.
    scheduled =
        scheduler.scheduleAtFixedRate(this::safeTick, 0, interval.toNanos(), TimeUnit.NANOSECONDS);
    LOG.info("Simulation started, one tick every {}", interval);
  }

  /** The executor silently stops a task that throws, so one failed tick must not escape. */
  private void safeTick() {
    try {
      tick.run();
    } catch (RuntimeException e) {
      LOG.error("Simulation tick failed; the next tick runs as scheduled", e);
    }
  }

  @Override
  public synchronized void stop() {
    if (scheduled == null) {
      return;
    }
    // false: let a tick in progress finish, so no tick is left half published.
    scheduled.cancel(false);
    scheduled = null;
    LOG.info("Simulation stopped");
  }

  @Override
  public synchronized boolean isRunning() {
    return scheduled != null;
  }
}
