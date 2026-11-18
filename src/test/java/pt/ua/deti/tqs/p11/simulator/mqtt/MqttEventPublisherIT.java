package pt.ua.deti.tqs.p11.simulator.mqtt;

import static org.assertj.core.api.Assertions.assertThat;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import pt.ua.deti.tqs.p11.simulator.contract.ContractSchemas;
import pt.ua.deti.tqs.p11.simulator.contract.ContractTopics;
import pt.ua.deti.tqs.p11.simulator.contract.EventMessageMapper;
import pt.ua.deti.tqs.p11.simulator.contract.RandomUuids;
import pt.ua.deti.tqs.p11.simulator.event.FaultEvent;
import pt.ua.deti.tqs.p11.simulator.event.FaultEventType;
import pt.ua.deti.tqs.p11.simulator.event.Severity;
import tools.jackson.databind.json.JsonMapper;

/** Publishes through a real Mosquitto broker and reads the event as a platform would. */
class MqttEventPublisherIT {

  private static final int MQTT_PORT = 1883;

  private static final GenericContainer<?> BROKER =
      new GenericContainer<>(DockerImageName.parse("eclipse-mosquitto:2.0.22"))
          .withCommand("mosquitto", "-c", "/mosquitto-no-auth.conf")
          .withExposedPorts(MQTT_PORT)
          .waitingFor(Wait.forListeningPort());

  private static Mqtt5AsyncClient simulatorClient;
  private static Mqtt5AsyncClient platformClient;

  @BeforeAll
  static void startBrokerAndClients() throws Exception {
    BROKER.start();
    simulatorClient = connectedClient("simulator");
    platformClient = connectedClient("platform");
  }

  @AfterAll
  static void stopBroker() {
    BROKER.stop();
  }

  private static Mqtt5AsyncClient connectedClient(String clientId) throws Exception {
    Mqtt5AsyncClient client =
        MqttClientFactory.create(
            new MqttProperties(
                BROKER.getHost(), BROKER.getMappedPort(MQTT_PORT), "", "", clientId));
    client.connect().get(10, TimeUnit.SECONDS);
    return client;
  }

  @Test
  void publish_sendsANonRetainedQos1ContractEventOnTheVehicleEventsTopic() throws Exception {
    MqttEventPublisher publisher =
        new MqttEventPublisher(
            simulatorClient,
            new EventMessageMapper(
                JsonMapper.builder().build(),
                Clock.systemUTC(),
                new RandomUuids(new java.util.Random(1))));
    FaultEvent event =
        new FaultEvent(
            "fleet-01",
            "bike-0042",
            FaultEventType.FAULT_RAISED,
            "BATTERY_OVERHEAT",
            Severity.CRITICAL);
    String topic = ContractTopics.events("fleet-01", "bike-0042");
    // Subscribed before publishing: a retained message would also arrive later, so the flag, not
    // the timing, tells whether it is retained.
    CompletableFuture<Mqtt5Publish> received = new CompletableFuture<>();
    platformClient
        .subscribeWith()
        .topicFilter(topic)
        .qos(MqttQos.AT_LEAST_ONCE)
        .callback(received::complete)
        .send()
        .get(10, TimeUnit.SECONDS);

    publisher.publish(event).get(10, TimeUnit.SECONDS);
    Mqtt5Publish message = received.get(10, TimeUnit.SECONDS);

    assertThat(message.getTopic()).hasToString(topic);
    assertThat(message.isRetain()).isFalse();
    assertThat(message.getQos()).isEqualTo(MqttQos.AT_LEAST_ONCE);
    String payload = new String(message.getPayloadAsBytes(), StandardCharsets.UTF_8);
    assertThat(ContractSchemas.validateEvent(payload)).isEmpty();
    assertThat(payload)
        .contains("\"eventType\":\"FAULT_RAISED\"", "\"faultCode\":\"BATTERY_OVERHEAT\"");
  }
}
