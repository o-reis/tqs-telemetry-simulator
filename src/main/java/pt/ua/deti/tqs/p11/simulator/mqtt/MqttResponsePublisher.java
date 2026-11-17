package pt.ua.deti.tqs.p11.simulator.mqtt;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.ua.deti.tqs.p11.simulator.command.ResponsePublisher;
import pt.ua.deti.tqs.p11.simulator.command.VehicleCommand;
import pt.ua.deti.tqs.p11.simulator.contract.ContractTopics;
import pt.ua.deti.tqs.p11.simulator.contract.ResponseMessageMapper;
import pt.ua.deti.tqs.p11.simulator.engine.CommandOutcome;

/**
 * Adapter: publishes the response to a command over MQTT as the contract requires (QoS 1, not
 * retained: a response describes something that happened once).
 */
public class MqttResponsePublisher implements ResponsePublisher {

  private static final Logger LOG = LoggerFactory.getLogger(MqttResponsePublisher.class);

  private final Mqtt5AsyncClient client;
  private final ResponseMessageMapper mapper;

  public MqttResponsePublisher(Mqtt5AsyncClient client, ResponseMessageMapper mapper) {
    this.client = client;
    this.mapper = mapper;
  }

  @Override
  public CompletableFuture<Void> publish(VehicleCommand command, CommandOutcome outcome) {
    String topic = ContractTopics.responses(command.fleetId(), command.vehicleId());
    CompletableFuture<Void> sent =
        client
            .publishWith()
            .topic(topic)
            .qos(MqttQos.AT_LEAST_ONCE)
            .retain(false)
            .payload(mapper.toJson(command, outcome).getBytes(StandardCharsets.UTF_8))
            .send()
            .thenApply(result -> null);
    // Logged here so a failure is visible even if the caller ignores the future.
    sent.exceptionally(error -> {
      LOG.warn("Response to command {} not published: {}", command.commandId(), error.getMessage());
      return null;
    });
    return sent;
  }
}
