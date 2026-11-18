package pt.ua.deti.tqs.p11.simulator.mqtt;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.ua.deti.tqs.p11.simulator.contract.ContractTopics;
import pt.ua.deti.tqs.p11.simulator.contract.EventMessageMapper;
import pt.ua.deti.tqs.p11.simulator.event.EventPublisher;
import pt.ua.deti.tqs.p11.simulator.event.FaultEvent;

/**
 * Adapter: publishes events over MQTT as the contract requires (QoS 1, not retained: an event
 * describes something that happened once, so a platform that connects later must not receive it).
 */
public class MqttEventPublisher implements EventPublisher {

  private static final Logger LOG = LoggerFactory.getLogger(MqttEventPublisher.class);

  private final Mqtt5AsyncClient client;
  private final EventMessageMapper mapper;

  public MqttEventPublisher(Mqtt5AsyncClient client, EventMessageMapper mapper) {
    this.client = client;
    this.mapper = mapper;
  }

  @Override
  public CompletableFuture<Void> publish(FaultEvent event) {
    String topic = ContractTopics.events(event.fleetId(), event.vehicleId());
    CompletableFuture<Void> sent =
        client
            .publishWith()
            .topic(topic)
            .qos(MqttQos.AT_LEAST_ONCE)
            .retain(false)
            .payload(mapper.toJson(event).getBytes(StandardCharsets.UTF_8))
            .send()
            .thenApply(result -> null);
    // Logged here so a failure is visible even if the caller ignores the future.
    sent.exceptionally(error -> {
      LOG.warn(
          "{} of {} on {} not published: {}",
          event.type(),
          event.faultCode(),
          event.vehicleId(),
          error.getMessage());
      return null;
    });
    return sent;
  }
}
