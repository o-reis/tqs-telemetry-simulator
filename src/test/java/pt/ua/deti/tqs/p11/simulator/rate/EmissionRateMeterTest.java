package pt.ua.deti.tqs.p11.simulator.rate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pt.ua.deti.tqs.p11.simulator.config.GeneratedFleet;
import pt.ua.deti.tqs.p11.simulator.config.HubConfig;
import pt.ua.deti.tqs.p11.simulator.config.ScenarioConfig;
import pt.ua.deti.tqs.p11.simulator.config.SimulationParams;
import pt.ua.deti.tqs.p11.simulator.telemetry.Position;

/** No real time: the clock is moved by hand. */
class EmissionRateMeterTest {

  private static final double TARGET = 10;

  private final MutableClock clock = new MutableClock(Instant.parse("2026-11-18T10:00:00Z"));
  private final EmissionRateMeter meter = new EmissionRateMeter(clock, TARGET);

  private void acknowledge(int readings) {
    for (int i = 0; i < readings; i++) {
      meter.recordSent();
      meter.recordAcknowledged();
    }
  }

  @Test
  void nextReport_withNothingPublished_isAllZero() {
    clock.advance(Duration.ofSeconds(10));

    RateReport report = meter.nextReport();

    assertThat(report).isEqualTo(new RateReport(Duration.ofSeconds(10), 0, 0, 0, TARGET));
  }

  @Test
  void nextReport_countsSentAcknowledgedAndFailed_sinceTheStart() {
    acknowledge(40);
    meter.recordSent();
    meter.recordFailed();
    clock.advance(Duration.ofSeconds(10));

    assertThat(meter.nextReport())
        .isEqualTo(new RateReport(Duration.ofSeconds(10), 41, 40, 1, TARGET));
  }

  @Test
  void nextReport_startsANewPeriod_thatCountsOnlyItsOwnReadings() {
    acknowledge(50);
    clock.advance(Duration.ofSeconds(10));
    meter.nextReport();

    acknowledge(30);
    clock.advance(Duration.ofSeconds(5));
    RateReport second = meter.nextReport();

    assertThat(second).isEqualTo(new RateReport(Duration.ofSeconds(5), 30, 30, 0, TARGET));
    assertThat(second.effectivePerSecond()).isEqualTo(6.0);
  }

  @Test
  void totalReport_coversTheWholeRun_andDoesNotStartANewPeriod() {
    acknowledge(50);
    clock.advance(Duration.ofSeconds(10));
    meter.nextReport();
    acknowledge(30);
    clock.advance(Duration.ofSeconds(10));

    RateReport total = meter.totalReport();

    assertThat(total).isEqualTo(new RateReport(Duration.ofSeconds(20), 80, 80, 0, TARGET));
    // The current period still holds its 30 readings.
    assertThat(meter.nextReport().acknowledged()).isEqualTo(30);
  }

  /** Acknowledgements arrive on the MQTT client's threads, not on the simulation thread. */
  @Test
  void counters_updatedFromSeveralThreadsAtOnce_loseNothing() throws Exception {
    ExecutorService threads = Executors.newFixedThreadPool(4);
    List<Future<?>> done = new ArrayList<>();
    try {
      IntStream.range(0, 4).forEach(t -> done.add(threads.submit(() -> acknowledge(10_000))));
      for (Future<?> f : done) {
        f.get();
      }
    } finally {
      threads.shutdown();
    }

    RateReport total = meter.totalReport();
    assertThat(total.sent()).isEqualTo(40_000);
    assertThat(total.acknowledged()).isEqualTo(40_000);
  }

  private static ScenarioConfig scenario(int vehicles, double intervalSeconds) {
    List<HubConfig> hubs =
        List.of(new HubConfig("hub-01", "Hub one", new Position(40.6443, -8.6455)));
    return new ScenarioConfig(
        "fleet-01",
        new SimulationParams(intervalSeconds, 1, 0),
        hubs,
        GeneratedFleet.vehicles(vehicles, "load", hubs));
  }

  @ParameterizedTest(name = "{0} vehicles every {1} s: {2} readings/s")
  @CsvSource({"1000, 1, 1000", "20, 10, 2", "100, 0.1, 1000", "1, 60, 0.016666666666666666"})
  void forScenario_targetIsTheVehiclesDividedByTheInterval(
      int vehicles, double interval, double target) {
    EmissionRateMeter forScenario =
        EmissionRateMeter.forScenario(Clock.systemUTC(), scenario(vehicles, interval));

    assertThat(forScenario.targetPerSecond()).isCloseTo(target, within(1e-9));
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
