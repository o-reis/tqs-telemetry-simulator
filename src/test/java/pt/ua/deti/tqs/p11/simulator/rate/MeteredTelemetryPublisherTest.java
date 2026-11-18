package pt.ua.deti.tqs.p11.simulator.rate;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import pt.ua.deti.tqs.p11.simulator.telemetry.LockState;
import pt.ua.deti.tqs.p11.simulator.telemetry.VehicleTelemetry;

/** The decorated publisher is a fake that hands back a future the test completes by hand. */
class MeteredTelemetryPublisherTest {
  private static final VehicleTelemetry READING =
      new VehicleTelemetry(
          "fleet-01", "bike-0001", 90, LockState.LOCKED, null, "hub-01", null, 0.0);

  private final List<VehicleTelemetry> delegated = new ArrayList<>();
  private final CompletableFuture<Void> brokerAnswer = new CompletableFuture<>();
  private final EmissionRateMeter meter = new EmissionRateMeter(Clock.systemUTC(), 10);

  private final MeteredTelemetryPublisher publisher =
      new MeteredTelemetryPublisher(
          t -> {
            delegated.add(t);
            return brokerAnswer;
          },
          meter);

  @Test
  void publish_passesTheReadingOn_andReturnsTheSameFuture() {
    CompletableFuture<Void> returned = publisher.publish(READING);

    assertThat(delegated).containsExactly(READING);
    assertThat(returned).isSameAs(brokerAnswer);
  }

  @Test
  void publish_beforeTheBrokerAnswers_countsTheReadingAsSentOnly() {
    publisher.publish(READING);

    assertThat(meter.totalReport())
        .extracting(RateReport::sent, RateReport::acknowledged, RateReport::failed)
        .containsExactly(1L, 0L, 0L);
  }

  @Test
  void publish_acknowledgedByTheBroker_countsTheReadingAsAcknowledged() {
    publisher.publish(READING);

    brokerAnswer.complete(null);

    assertThat(meter.totalReport())
        .extracting(RateReport::sent, RateReport::acknowledged, RateReport::failed)
        .containsExactly(1L, 1L, 0L);
  }

  @Test
  void publish_failedAtTheBroker_countsTheReadingAsFailed() {
    publisher.publish(READING);

    brokerAnswer.completeExceptionally(new IllegalStateException("broker unreachable"));

    assertThat(meter.totalReport())
        .extracting(RateReport::sent, RateReport::acknowledged, RateReport::failed)
        .containsExactly(1L, 0L, 1L);
  }
}
