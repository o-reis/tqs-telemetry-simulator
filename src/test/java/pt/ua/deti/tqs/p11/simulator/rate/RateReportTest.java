package pt.ua.deti.tqs.p11.simulator.rate;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RateReportTest {
  private static final Duration TEN_SECONDS = Duration.ofSeconds(10);

  private static RateReport acknowledgedInTenSeconds(long acknowledged, double target) {
    return new RateReport(TEN_SECONDS, acknowledged, acknowledged, 0, target);
  }

  @Test
  void effectivePerSecond_isTheAcknowledgedReadingsPerSecond() {
    assertThat(acknowledgedInTenSeconds(50, 10).effectivePerSecond()).isEqualTo(5.0);
  }

  @Test
  void effectivePerSecond_countsOnlyAcknowledgedReadings_notSentOnes() {
    RateReport report = new RateReport(TEN_SECONDS, 100, 80, 20, 10);

    assertThat(report.effectivePerSecond()).isEqualTo(8.0);
  }

  @Test
  void effectivePerSecond_withSubSecondPrecision() {
    RateReport report = new RateReport(Duration.ofMillis(2500), 10, 10, 0, 4);

    assertThat(report.effectivePerSecond()).isEqualTo(4.0);
  }

  @Test
  void effectivePerSecond_ofAPeriodWithNoLength_isZero() {
    assertThat(new RateReport(Duration.ZERO, 5, 5, 0, 10).effectivePerSecond()).isZero();
  }

  // ---- Below target: under 95 % of the configured rate ----

  @ParameterizedTest(name = "{0} acknowledged in 10 s, target 10/s: below target = {1}")
  @CsvSource({"100, false", "95, false", "94, true", "0, true", "150, false"})
  void belowTarget_isUnder95PercentOfTheTarget(long acknowledged, boolean below) {
    assertThat(acknowledgedInTenSeconds(acknowledged, 10).belowTarget()).isEqualTo(below);
  }

  // ---- The log line ----

  @Test
  void describe_givesTheRatesTheCountsAndThePeriod() {
    RateReport report = new RateReport(TEN_SECONDS, 9990, 9984, 6, 1000);

    assertThat(report.describe())
        .isEqualTo(
            "Effective rate: 998.4 readings/s (target 1000.0): "
                + "9990 sent, 9984 acknowledged, 6 failed in 10.0 s");
  }
}
