package pt.ua.deti.tqs.p11.simulator.mqtt;

import static org.assertj.core.api.Assertions.assertThat;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import pt.ua.deti.tqs.p11.simulator.command.CommandService;
import pt.ua.deti.tqs.p11.simulator.config.HubConfig;
import pt.ua.deti.tqs.p11.simulator.config.ScenarioConfig;
import pt.ua.deti.tqs.p11.simulator.config.SimulationParams;
import pt.ua.deti.tqs.p11.simulator.config.VehicleConfig;
import pt.ua.deti.tqs.p11.simulator.config.VehicleType;
import pt.ua.deti.tqs.p11.simulator.contract.CommandMessageMapper;
import pt.ua.deti.tqs.p11.simulator.contract.ContractSchemas;
import pt.ua.deti.tqs.p11.simulator.contract.ContractTopics;
import pt.ua.deti.tqs.p11.simulator.contract.ResponseMessageMapper;
import pt.ua.deti.tqs.p11.simulator.contract.TelemetryMessageMapper;
import pt.ua.deti.tqs.p11.simulator.engine.SimulationEngine;
import pt.ua.deti.tqs.p11.simulator.telemetry.Position;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The platform's side of the commands, through a real Mosquitto broker: the test sends commands as
 * a platform would and reads the responses and the telemetry back.
 */
class MqttCommandsIT {
  private static final int MQTT_PORT = 1883;
  private static final long TIMEOUT_SECONDS = 10;

  private static final GenericContainer<?> BROKER =
      new GenericContainer<>(DockerImageName.parse("eclipse-mosquitto:2.0.22"))
          .withCommand("mosquitto", "-c", "/mosquitto-no-auth.conf")
          .withExposedPorts(MQTT_PORT)
          .waitingFor(Wait.forListeningPort());

  private static final JsonMapper JSON = JsonMapper.builder().build();

  /** One bike at hub-01, two hubs, no faults and no automatic trips. */
  private static final ScenarioConfig SCENARIO =
      new ScenarioConfig(
          "fleet-01",
          new SimulationParams(10, 7, 0, 0, 0, 0, 15),
          List.of(
              new HubConfig("hub-01", "Hub one", new Position(40.6443, -8.6455)),
              new HubConfig("hub-02", "Hub two", new Position(40.6412, -8.6501))),
          List.of(new VehicleConfig("bike-0001", VehicleType.E_BIKE, "hub-01", 90)));

  private static ExecutorService simulationThread;
  private static SimulationEngine engine;

  @BeforeAll
  static void startBrokerAndSimulator() throws Exception {
    BROKER.start();
    Mqtt5AsyncClient simulatorClient = connectedClient("simulator");
    Clock clock = Clock.systemUTC();
    engine =
        new SimulationEngine(
            SCENARIO,
            new MqttTelemetryPublisher(simulatorClient, new TelemetryMessageMapper(JSON, clock)),
            new Random(7));
    simulationThread = Executors.newSingleThreadExecutor();
    CommandService service =
        new CommandService(
            engine,
            new MqttResponsePublisher(
                simulatorClient, new ResponseMessageMapper(JSON, clock, UUID::randomUUID)),
            clock,
            simulationThread);
    new MqttCommandSubscriber(
            simulatorClient, "fleet-01", new CommandMessageMapper(JSON), service::submit)
        .subscribe()
        .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
  }

  @AfterAll
  static void stopSimulatorAndBroker() {
    simulationThread.shutdownNow();
    BROKER.stop();
  }

  private static Mqtt5AsyncClient connectedClient(String clientId) throws Exception {
    Mqtt5AsyncClient client =
        MqttClientFactory.create(
            new MqttProperties(
                BROKER.getHost(), BROKER.getMappedPort(MQTT_PORT), "", "", clientId));
    client.connect().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    return client;
  }

  private static String commandJson(String vehicleId, UUID commandId) {
    return JSON.writeValueAsString(
        Map.of(
            "schemaVersion",
            "1.0.0",
            "messageId",
            UUID.randomUUID().toString(),
            "type",
            "command",
            "timestamp",
            Instant.now().toString(),
            "fleetId",
            "fleet-01",
            "vehicleId",
            vehicleId,
            "data",
            Map.of(
                "commandId", commandId.toString(),
                "expiresAt", Instant.now().plusSeconds(30).toString())));
  }

  private static void send(Mqtt5AsyncClient platform, String vehicleId, String name, String json)
      throws Exception {
    platform
        .publishWith()
        .topic("fleet/fleet-01/vehicles/" + vehicleId + "/commands/" + name)
        .qos(MqttQos.AT_LEAST_ONCE)
        .payload(json.getBytes(StandardCharsets.UTF_8))
        .send()
        .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
  }

  /** Subscribes (waiting for the broker's acknowledgement) and collects every message received. */
  private static List<String> collect(Mqtt5AsyncClient platform, String topic) throws Exception {
    List<String> received = new CopyOnWriteArrayList<>();
    platform
        .subscribeWith()
        .topicFilter(topic)
        .qos(MqttQos.AT_LEAST_ONCE)
        .callback(m -> received.add(new String(m.getPayloadAsBytes(), StandardCharsets.UTF_8)))
        .send()
        .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    return received;
  }

  /** Waits until a received message matches, without sleeping longer than needed. */
  private static JsonNode awaitMessage(List<String> received, Predicate<JsonNode> matches)
      throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
    while (System.nanoTime() < deadline) {
      for (String json : received) {
        JsonNode message = JSON.readTree(json);
        if (matches.test(message)) {
          return message;
        }
      }
      TimeUnit.MILLISECONDS.sleep(50);
    }
    throw new AssertionError("No matching message within " + TIMEOUT_SECONDS + " s: " + received);
  }

  private static Predicate<JsonNode> answers(UUID commandId) {
    return m -> commandId.toString().equals(m.get("data").get("commandId").asString());
  }

  @Test
  void unlock_isAnsweredCompleted_andTheNextTelemetryShowsTheVehicleUnlocked() throws Exception {
    Mqtt5AsyncClient platform = connectedClient("platform-unlock");
    List<String> responses = collect(platform, ContractTopics.responses("fleet-01", "bike-0001"));
    List<String> telemetry = collect(platform, ContractTopics.telemetry("fleet-01", "bike-0001"));
    UUID commandId = UUID.randomUUID();

    send(platform, "bike-0001", "unlock", commandJson("bike-0001", commandId));

    JsonNode response = awaitMessage(responses, answers(commandId));
    assertThat(response.get("data").get("command").asString()).isEqualTo("unlock");
    assertThat(response.get("data").get("status").asString()).isEqualTo("COMPLETED");
    assertThat(response.get("data").get("reason").isNull()).isTrue();
    assertThat(ContractSchemas.validateResponse(response.toString())).isEmpty();

    simulationThread.execute(engine::tick);

    JsonNode unlocked =
        awaitMessage(telemetry, m -> "unlocked".equals(m.get("data").get("lockState").asString()));
    assertThat(ContractSchemas.validateTelemetry(unlocked.toString())).isEmpty();
  }

  @Test
  void commandForAnUnknownVehicle_isAnsweredRejectedWithUnknownVehicle() throws Exception {
    Mqtt5AsyncClient platform = connectedClient("platform-unknown");
    List<String> responses = collect(platform, ContractTopics.responses("fleet-01", "bike-9999"));
    UUID commandId = UUID.randomUUID();

    send(platform, "bike-9999", "lock", commandJson("bike-9999", commandId));

    JsonNode response = awaitMessage(responses, answers(commandId));
    assertThat(response.get("data").get("status").asString()).isEqualTo("REJECTED");
    assertThat(response.get("data").get("reason").asString()).isEqualTo("UNKNOWN_VEHICLE");
    assertThat(ContractSchemas.validateResponse(response.toString())).isEmpty();
  }

  @Test
  void invalidMessage_getsNoResponse_andLaterCommandsAreStillAnswered() throws Exception {
    Mqtt5AsyncClient platform = connectedClient("platform-invalid");
    List<String> responses = collect(platform, ContractTopics.responses("fleet-01", "bike-0001"));
    UUID pingId = UUID.randomUUID();

    send(platform, "bike-0001", "ping", "{ not json");
    send(platform, "bike-0001", "ping", commandJson("bike-0001", pingId));

    awaitMessage(responses, answers(pingId));
    // Messages on one topic arrive in order: had the invalid one been answered, it would be here.
    assertThat(responses).hasSize(1);
  }
}
