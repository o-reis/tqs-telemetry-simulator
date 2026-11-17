package pt.ua.deti.tqs.p11.simulator.mqtt;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import pt.ua.deti.tqs.p11.simulator.command.CommandName;
import pt.ua.deti.tqs.p11.simulator.command.VehicleCommand;
import pt.ua.deti.tqs.p11.simulator.contract.ResponseMessageMapper;
import pt.ua.deti.tqs.p11.simulator.engine.CommandOutcome;
import tools.jackson.databind.json.JsonMapper;

/** The success path is covered by MqttCommandsIT, against a real broker. */
class MqttResponsePublisherTest {

  @Test
  void publish_withoutABrokerConnection_completesExceptionally() {
    MqttResponsePublisher publisher =
        new MqttResponsePublisher(
            MqttClientFactory.create(new MqttProperties("localhost", 1883, "", "", "unit-test")),
            new ResponseMessageMapper(
                JsonMapper.builder().build(),
                Clock.systemUTC(),
                () -> UUID.fromString("1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c05")));
    VehicleCommand ping =
        new VehicleCommand(
            "fleet-01",
            "bike-0042",
            CommandName.PING,
            UUID.fromString("e5b2a9c1-0d3f-4a8e-8b77-1c2d3e4f5a04"),
            null);

    assertThat(publisher.publish(ping, CommandOutcome.completed()))
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableOfType(ExecutionException.class);
  }
}
