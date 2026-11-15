package pt.ua.deti.tqs.p11.simulator.mqtt;

import static org.assertj.core.api.Assertions.assertThat;

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
import pt.ua.deti.tqs.p11.simulator.contract.TelemetryMessageMapper;
import pt.ua.deti.tqs.p11.simulator.telemetry.LockState;
import pt.ua.deti.tqs.p11.simulator.telemetry.VehicleTelemetry;
import tools.jackson.databind.json.JsonMapper;

/** Publishes through a real Mosquitto broker and reads the message back as a platform would. */
class MqttTelemetryPublisherIT {

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
  void publish_sendsARetainedContractMessageOnTheVehicleTelemetryTopic() throws Exception {
    MqttTelemetryPublisher publisher =
        new MqttTelemetryPublisher(
            simulatorClient,
            new TelemetryMessageMapper(JsonMapper.builder().build(), Clock.systemUTC()));
    VehicleTelemetry telemetry =
        new VehicleTelemetry(
            "fleet-01", "bike-0042", 64.0, LockState.LOCKED, null, "hub-03", null, null);
    String topic = ContractTopics.telemetry("fleet-01", "bike-0042");

    publisher.publish(telemetry).get(10, TimeUnit.SECONDS);

    // Subscribing after the publish proves the message is retained for late platforms.
    CompletableFuture<Mqtt5Publish> received = new CompletableFuture<>();
    platformClient.subscribeWith().topicFilter(topic).callback(received::complete).send();
    Mqtt5Publish message = received.get(10, TimeUnit.SECONDS);

    assertThat(message.getTopic()).hasToString(topic);
    assertThat(message.isRetain()).isTrue();
    String payload = new String(message.getPayloadAsBytes(), StandardCharsets.UTF_8);
    assertThat(ContractSchemas.validateTelemetry(payload)).isEmpty();
  }
}
