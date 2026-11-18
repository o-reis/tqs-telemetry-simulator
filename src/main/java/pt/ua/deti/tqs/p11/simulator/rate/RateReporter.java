package pt.ua.deti.tqs.p11.simulator.rate;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/**
 * Writes the effective emission rate to the log: the target at start-up, a report every period (a
 * warning when the rate is not sustained) and a summary of the whole run when the application
 * stops.
 */
public class RateReporter implements SmartLifecycle {

  /** How often a report is written. */
  public static final Duration DEFAULT_PERIOD = Duration.ofSeconds(10);

  private static final Logger LOG = LoggerFactory.getLogger(RateReporter.class);

  /** Where the lines go: the application's log, or a list in the tests. */
  interface ReportLog {
    void info(String line);

    void warn(String line);
  }

  private static final ReportLog SLF4J =
      new ReportLog() {
        @Override
        public void info(String line) {
          LOG.info("{}", line);
        }

        @Override
        public void warn(String line) {
          LOG.warn("{}", line);
        }
      };

  private final EmissionRateMeter meter;
  private final Duration period;
  private final ScheduledExecutorService scheduler;
  private final ReportLog log;
  private ScheduledFuture<?> scheduled;

  /**
   * @param scheduler normally the simulation's executor, so no thread is added
   */
  public RateReporter(
      EmissionRateMeter meter, Duration period, ScheduledExecutorService scheduler) {
    this(meter, period, scheduler, SLF4J);
  }

  RateReporter(
      EmissionRateMeter meter, Duration period, ScheduledExecutorService scheduler, ReportLog log) {
    this.meter = meter;
    this.period = period;
    this.scheduler = scheduler;
    this.log = log;
  }

  @Override
  public synchronized void start() {
    if (scheduled != null) {
      return;
    }
    log.info(
        String.format(
            Locale.ROOT,
            "Target rate: %.1f readings/s, a report every %d s",
            meter.targetPerSecond(),
            period.toSeconds()));
    scheduled =
        scheduler.scheduleAtFixedRate(
            this::reportPeriod, period.toNanos(), period.toNanos(), TimeUnit.NANOSECONDS);
  }

  /** Writes the report of the period that just ended. */
  void reportPeriod() {
    write("", meter.nextReport());
  }

  @Override
  public synchronized void stop() {
    if (scheduled == null) {
      return;
    }
    scheduled.cancel(false);
    scheduled = null;
    write("Run summary: ", meter.totalReport());
  }

  @Override
  public synchronized boolean isRunning() {
    return scheduled != null;
  }

  /**
   * Lower than the simulation runner's phase: started before it and stopped after it, so the run
   * summary includes the last tick.
   */
  @Override
  public int getPhase() {
    return SmartLifecycle.DEFAULT_PHASE - 1;
  }

  private void write(String prefix, RateReport report) {
    if (report.belowTarget()) {
      log.warn(
          prefix
              + report.describe()
              + " - below 95 % of the target: the configured rate is not sustained");
    } else {
      log.info(prefix + report.describe());
    }
  }
}
