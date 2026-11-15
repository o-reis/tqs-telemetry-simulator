package pt.ua.deti.tqs.p11.simulator.mqtt;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.ua.deti.tqs.p11.simulator.contract.ContractTopics;
import pt.ua.deti.tqs.p11.simulator.contract.TelemetryMessageMapper;
import pt.ua.deti.tqs.p11.simulator.telemetry.TelemetryPublisher;
import pt.ua.deti.tqs.p11.simulator.telemetry.VehicleTelemetry;

/**
 * Adapter: publishes telemetry over MQTT as the contract requires (QoS 1, retained, one topic per
 * vehicle).
 */
public class MqttTelemetryPublisher implements TelemetryPublisher {

  private static final Logger LOG = LoggerFactory.getLogger(MqttTelemetryPublisher.class);

  private final Mqtt5AsyncClient client;
  private final TelemetryMessageMapper mapper;

  public MqttTelemetryPublisher(Mqtt5AsyncClient client, TelemetryMessageMapper mapper) {
    this.client = client;
    this.mapper = mapper;
  }

  @Override
  public CompletableFuture<Void> publish(VehicleTelemetry telemetry) {
    String topic = ContractTopics.telemetry(telemetry.fleetId(), telemetry.vehicleId());
    CompletableFuture<Void> sent =
        client
            .publishWith()
            .topic(topic)
            .qos(MqttQos.AT_LEAST_ONCE)
            .retain(true)
            .payload(mapper.toJson(telemetry).getBytes(StandardCharsets.UTF_8))
            .send()
            .thenApply(result -> null);
    // Logged here so a failure is visible even if the caller ignores the future.
    sent.exceptionally(error -> {
      LOG.warn("Telemetry not published on {}: {}", topic, error.getMessage());
      return null;
    });
    return sent;
  }
}
