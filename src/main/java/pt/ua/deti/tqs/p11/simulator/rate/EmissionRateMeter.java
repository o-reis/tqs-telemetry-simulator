package pt.ua.deti.tqs.p11.simulator.rate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.LongAdder;
import pt.ua.deti.tqs.p11.simulator.config.ScenarioConfig;

/**
 * Counts the readings published during a run. The counters can be updated from any thread: the
 * broker's acknowledgements arrive on the MQTT client's threads.
 */
public class EmissionRateMeter {

  private final Clock clock;
  private final double targetPerSecond;
  private final Instant start;

  private final LongAdder sent = new LongAdder();
  private final LongAdder acknowledged = new LongAdder();
  private final LongAdder failed = new LongAdder();

  /** Totals at the end of the previous period, so each report counts only its own period. */
  private Instant periodStart;

  private long sentBefore;
  private long acknowledgedBefore;
  private long failedBefore;

  /**
   * @param targetPerSecond the configured rate: number of vehicles divided by the emission interval
   */
  public EmissionRateMeter(Clock clock, double targetPerSecond) {
    this.clock = clock;
    this.targetPerSecond = targetPerSecond;
    this.start = clock.instant();
    this.periodStart = start;
  }

  /** A meter whose target is the scenario's rate: one reading per vehicle every interval. */
  public static EmissionRateMeter forScenario(Clock clock, ScenarioConfig scenario) {
    return new EmissionRateMeter(
        clock, scenario.vehicles().size() / scenario.simulation().emissionIntervalSeconds());
  }

  public double targetPerSecond() {
    return targetPerSecond;
  }

  public void recordSent() {
    sent.increment();
  }

  public void recordAcknowledged() {
    acknowledged.increment();
  }

  public void recordFailed() {
    failed.increment();
  }

  /** The report since the previous call (or the start), and the start of a new period. */
  public synchronized RateReport nextReport() {
    Instant now = clock.instant();
    long sentNow = sent.sum();
    long acknowledgedNow = acknowledged.sum();
    long failedNow = failed.sum();
    RateReport report =
        new RateReport(
            Duration.between(periodStart, now),
            sentNow - sentBefore,
            acknowledgedNow - acknowledgedBefore,
            failedNow - failedBefore,
            targetPerSecond);
    periodStart = now;
    sentBefore = sentNow;
    acknowledgedBefore = acknowledgedNow;
    failedBefore = failedNow;
    return report;
  }

  /** The report of the whole run so far. */
  public RateReport totalReport() {
    return new RateReport(
        Duration.between(start, clock.instant()),
        sent.sum(),
        acknowledged.sum(),
        failed.sum(),
        targetPerSecond);
  }
}
