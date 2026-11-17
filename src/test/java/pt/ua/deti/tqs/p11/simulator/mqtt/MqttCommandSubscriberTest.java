package pt.ua.deti.tqs.p11.simulator.mqtt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;
import pt.ua.deti.tqs.p11.simulator.command.CommandName;
import pt.ua.deti.tqs.p11.simulator.command.VehicleCommand;
import pt.ua.deti.tqs.p11.simulator.contract.CommandMessageMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * What the subscriber does with each message, without a broker. The client is created but never
 * connected: these tests call the message handler directly.
 */
class MqttCommandSubscriberTest {
  private static final String UNLOCK_TOPIC = "fleet/fleet-01/vehicles/bike-0042/commands/unlock";

  private final Mqtt5AsyncClient notConnected =
      MqttClientFactory.create(new MqttProperties("localhost", 1883, "", "", "unit-test"));
  private final CommandMessageMapper mapper =
      new CommandMessageMapper(JsonMapper.builder().build());
  private final List<VehicleCommand> submitted = new ArrayList<>();

  private final MqttCommandSubscriber subscriber =
      new MqttCommandSubscriber(notConnected, "fleet-01", mapper, submitted::add);

  private static byte[] exampleUnlock() throws IOException {
    return Files.readAllBytes(Path.of("contract", "examples", "valid", "command-unlock.json"));
  }

  @Test
  void validCommand_isSubmitted() throws IOException {
    subscriber.onMessage(UNLOCK_TOPIC, exampleUnlock());

    assertThat(submitted)
        .singleElement()
        .satisfies(command -> {
          assertThat(command.name()).isEqualTo(CommandName.UNLOCK);
          assertThat(command.vehicleId()).isEqualTo("bike-0042");
        });
  }

  @Test
  void invalidMessage_isDropped_withoutThrowing() {
    byte[] notJson = "{".getBytes(StandardCharsets.UTF_8);

    assertThatCode(() -> subscriber.onMessage(UNLOCK_TOPIC, notJson)).doesNotThrowAnyException();
    assertThat(submitted).isEmpty();
  }

  /** An exception thrown into the MQTT client's callback could stop the subscription. */
  @Test
  void failureWhileSubmitting_doesNotEscapeTheCallback() throws IOException {
    MqttCommandSubscriber shuttingDown =
        new MqttCommandSubscriber(
            notConnected,
            "fleet-01",
            mapper,
            command -> {
              throw new RejectedExecutionException("simulation is shutting down");
            });
    byte[] unlock = exampleUnlock();

    assertThatCode(() -> shuttingDown.onMessage(UNLOCK_TOPIC, unlock)).doesNotThrowAnyException();
  }
}
