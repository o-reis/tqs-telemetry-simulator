package pt.ua.deti.tqs.p11.simulator.mqtt;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import pt.ua.deti.tqs.p11.simulator.contract.EventMessageMapper;
import pt.ua.deti.tqs.p11.simulator.event.FaultEvent;
import pt.ua.deti.tqs.p11.simulator.event.FaultEventType;
import pt.ua.deti.tqs.p11.simulator.event.Severity;
import tools.jackson.databind.json.JsonMapper;

/** The success path is covered by MqttEventPublisherIT, against a real broker. */
class MqttEventPublisherTest {

  @Test
  void publish_withoutABrokerConnection_completesExceptionally() {
    MqttEventPublisher publisher =
        new MqttEventPublisher(
            MqttClientFactory.create(new MqttProperties("localhost", 1883, "", "", "unit-test")),
            new EventMessageMapper(
                JsonMapper.builder().build(),
                Clock.systemUTC(),
                () -> UUID.fromString("0b8e5a62-5d6e-4c0a-8d11-2f6c0b9f4a02")));
    FaultEvent event =
        new FaultEvent(
            "fleet-01", "bike-0042", FaultEventType.FAULT_RAISED, "BRAKE_WEAR", Severity.WARNING);

    assertThat(publisher.publish(event))
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableOfType(ExecutionException.class);
  }
}
