package pt.ua.deti.tqs.p11.simulator.rate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.SmartLifecycle;

/**
 * No real time and no real thread: the clock is moved by hand, the scheduler is a mock (it is the
 * boundary to real time) and the log lines go to two lists.
 */
class RateReporterTest {

  private static final Duration PERIOD = Duration.ofSeconds(10);

  private final MutableClock clock = new MutableClock(Instant.parse("2026-11-18T10:00:00Z"));
  private final EmissionRateMeter meter = new EmissionRateMeter(clock, 10);
  private final ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
  private final ScheduledFuture<?> scheduled = mock(ScheduledFuture.class);
  private final List<String> infos = new ArrayList<>();
  private final List<String> warnings = new ArrayList<>();

  private final RateReporter reporter =
      new RateReporter(
          meter,
          PERIOD,
          scheduler,
          new RateReporter.ReportLog() {
            @Override
            public void info(String line) {
              infos.add(line);
            }

            @Override
            public void warn(String line) {
              warnings.add(line);
            }
          });

  @BeforeEach
  void schedulerReturnsAFuture() {
    doReturn(scheduled)
        .when(scheduler)
        .scheduleAtFixedRate(
            any(Runnable.class),
            eq(PERIOD.toNanos()),
            eq(PERIOD.toNanos()),
            eq(TimeUnit.NANOSECONDS));
  }

  private void acknowledge(int readings) {
    for (int i = 0; i < readings; i++) {
      meter.recordSent();
      meter.recordAcknowledged();
    }
  }

  // ---- Start ----

  @Test
  void start_logsTheTarget_andSchedulesAReportEveryPeriod() {
    reporter.start();

    assertThat(infos).containsExactly("Target rate: 10.0 readings/s, a report every 10 s");
    verify(scheduler)
        .scheduleAtFixedRate(
            any(Runnable.class),
            eq(PERIOD.toNanos()),
            eq(PERIOD.toNanos()),
            eq(TimeUnit.NANOSECONDS));
    assertThat(reporter.isRunning()).isTrue();
  }

  // ---- Every period ----

  @Test
  void reportPeriod_atTheTarget_isLoggedAsInformation() {
    acknowledge(100);
    clock.advance(PERIOD);

    reporter.reportPeriod();

    assertThat(infos)
        .containsExactly(
            "Effective rate: 10.0 readings/s (target 10.0): "
                + "100 sent, 100 acknowledged, 0 failed in 10.0 s");
    assertThat(warnings).isEmpty();
  }

  @Test
  void reportPeriod_belowTheTarget_isLoggedAsAWarning() {
    acknowledge(50);
    clock.advance(PERIOD);

    reporter.reportPeriod();

    assertThat(infos).isEmpty();
    assertThat(warnings)
        .singleElement()
        .asString()
        .startsWith("Effective rate: 5.0 readings/s (target 10.0)")
        .endsWith("below 95 % of the target: the configured rate is not sustained");
  }

  // ---- Stop: the report of the whole run ----

  @Test
  void stop_logsASummaryOfTheWholeRun_andCancelsTheSchedule() {
    reporter.start();
    acknowledge(100);
    clock.advance(PERIOD);
    reporter.reportPeriod();
    acknowledge(100);
    clock.advance(PERIOD);

    reporter.stop();

    assertThat(infos)
        .last()
        .isEqualTo(
            "Run summary: Effective rate: 10.0 readings/s (target 10.0): "
                + "200 sent, 200 acknowledged, 0 failed in 20.0 s");
    // false: a report being written is allowed to finish.
    verify(scheduled).cancel(false);
    assertThat(reporter.isRunning()).isFalse();
  }

  @Test
  void stop_withoutStart_writesNothing() {
    reporter.stop();

    assertThat(infos).isEmpty();
    assertThat(warnings).isEmpty();
    verifyNoInteractions(scheduler);
  }

  /** The runner keeps the default phase; a lower phase stops later. */
  @Test
  void phase_isLowerThanTheRunners_soTheSummaryIncludesTheLastTick() {
    assertThat(reporter.getPhase()).isLessThan(SmartLifecycle.DEFAULT_PHASE);
  }

  /** A clock the test moves by hand. */
  private static final class MutableClock extends Clock {
    private Instant now;

    private MutableClock(Instant now) {
      this.now = now;
    }

    private void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public Instant instant() {
      return now;
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }
  }
}
