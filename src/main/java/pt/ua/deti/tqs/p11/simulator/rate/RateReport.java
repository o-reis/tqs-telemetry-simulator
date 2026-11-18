package pt.ua.deti.tqs.p11.simulator.rate;

import java.time.Duration;
import java.util.Locale;

/**
 * How many readings were published during a period, and whether the rate kept up with the target.
 * The effective rate counts the readings the broker acknowledged (QoS 1): a reading still waiting
 * in a buffer has not reached anyone.
 *
 * @param period how long the period lasted
 * @param targetPerSecond the configured rate: number of vehicles divided by the emission interval
 */
public record RateReport(
    Duration period, long sent, long acknowledged, long failed, double targetPerSecond) {

  /**
   * Below 95 % of the target the rate is not sustained. The margin absorbs a tick that falls on
   * either side of the period's edge.
   */
  static final double SUSTAINED_RATIO = 0.95;

  private static final double NANOS_PER_SECOND = 1_000_000_000d;

  /** Acknowledged readings per second; 0 for a period of no length. */
  public double effectivePerSecond() {
    double seconds = period.toNanos() / NANOS_PER_SECOND;
    return seconds > 0 ? acknowledged / seconds : 0;
  }

  /** Whether the effective rate stayed under 95 % of the target. */
  public boolean belowTarget() {
    return effectivePerSecond() / targetPerSecond < SUSTAINED_RATIO;
  }

  /** One line for the log. */
  public String describe() {
    return String.format(
        Locale.ROOT,
        "Effective rate: %.1f readings/s (target %.1f): "
            + "%d sent, %d acknowledged, %d failed in %.1f s",
        effectivePerSecond(),
        targetPerSecond,
        sent,
        acknowledged,
        failed,
        period.toNanos() / NANOS_PER_SECOND);
  }
}
