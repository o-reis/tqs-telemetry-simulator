package pt.ua.deti.tqs.p11.simulator.rate;

import static org.assertj.core.api.Assertions.assertThat;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import pt.ua.deti.tqs.p11.simulator.config.ScenarioConfig;
import pt.ua.deti.tqs.p11.simulator.config.ScenarioLoader;
import pt.ua.deti.tqs.p11.simulator.config.ScenarioParser;
import pt.ua.deti.tqs.p11.simulator.contract.ContractTopics;
import pt.ua.deti.tqs.p11.simulator.contract.TelemetryMessageMapper;
import pt.ua.deti.tqs.p11.simulator.engine.SimulationEngine;
import pt.ua.deti.tqs.p11.simulator.engine.SimulationRunner;
import pt.ua.deti.tqs.p11.simulator.mqtt.MqttClientFactory;
import pt.ua.deti.tqs.p11.simulator.mqtt.MqttProperties;
import pt.ua.deti.tqs.p11.simulator.mqtt.MqttTelemetryPublisher;
import tools.jackson.databind.json.JsonMapper;

/**
 * The high-rate mode in real time, through a real Mosquitto broker: the load test scenario runs for
 * a few seconds while a platform client counts the readings it receives.
 *
 * <p>The assertions use the SLO rate, not the scenario's much higher target, so a slow CI runner
 * does not fail the build; the rate actually achieved is written to the log.
 */
class HighRateIT {

  private static final Logger LOG = LoggerFactory.getLogger(HighRateIT.class);

  private static final int MQTT_PORT = 1883;
  private static final long TIMEOUT_SECONDS = 10;
  private static final double NANOS_PER_SECOND = 1_000_000_000d;

  /** SLO2 (Product Specification): telemetry lag under 5 s at 10 readings/s. */
  private static final double SLO2_READINGS_PER_SECOND = 10;

  private static final Duration MEASURED = Duration.ofSeconds(6);

  private static final Path SCENARIO =
      Path.of("src", "main", "resources", "scenarios", "load-test.yml");

  private static final GenericContainer<?> BROKER =
      new GenericContainer<>(DockerImageName.parse("eclipse-mosquitto:2.0.22"))
          .withCommand("mosquitto", "-c", "/mosquitto-no-auth.conf")
          .withExposedPorts(MQTT_PORT)
          .waitingFor(Wait.forListeningPort());

  @BeforeAll
  static void startBroker() {
    BROKER.start();
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
    client.connect().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    return client;
  }

  private static ScenarioConfig loadTestScenario() throws Exception {
    try (InputStream input = Files.newInputStream(SCENARIO)) {
      return new ScenarioLoader(new ScenarioParser()).load(input, SCENARIO.toString());
    }
  }

  @Test
  void loadTestScenario_sustainsAtLeastTheTelemetrySloRate_throughARealBroker() throws Exception {
    ScenarioConfig scenario = loadTestScenario();
    Mqtt5AsyncClient simulatorClient = connectedClient("simulator");
    Mqtt5AsyncClient platformClient = connectedClient("platform");
    LongAdder received = new LongAdder();
    platformClient
        .subscribeWith()
        .topicFilter(ContractTopics.telemetry(scenario.fleetId(), "+"))
        .qos(MqttQos.AT_LEAST_ONCE)
        .callback(message -> received.increment())
        .send()
        .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

    Clock clock = Clock.systemUTC();
    EmissionRateMeter meter = EmissionRateMeter.forScenario(clock, scenario);
    SimulationEngine engine =
        new SimulationEngine(
            scenario,
            new MeteredTelemetryPublisher(
                new MqttTelemetryPublisher(
                    simulatorClient,
                    new TelemetryMessageMapper(JsonMapper.builder().build(), clock)),
                meter),
            new Random(scenario.simulation().seed()));
    ScheduledExecutorService simulationThread = Executors.newSingleThreadScheduledExecutor();
    SimulationRunner runner =
        new SimulationRunner(
            engine::tick,
            Duration.ofNanos(
                Math.round(scenario.simulation().emissionIntervalSeconds() * NANOS_PER_SECOND)),
            simulationThread);

    try {
      long start = System.nanoTime();
      runner.start();
      TimeUnit.NANOSECONDS.sleep(MEASURED.toNanos());
      runner.stop();
      double seconds = (System.nanoTime() - start) / NANOS_PER_SECOND;

      RateReport report = meter.totalReport();
      double receivedPerSecond = received.sum() / seconds;
      LOG.info(
          "High-rate run: {}; the platform received {} readings ({} readings/s)",
          report.describe(),
          received.sum(),
          String.format(Locale.ROOT, "%.1f", receivedPerSecond));

      assertThat(report.failed()).isZero();
      assertThat(report.effectivePerSecond()).isGreaterThanOrEqualTo(SLO2_READINGS_PER_SECOND);
      assertThat(receivedPerSecond).isGreaterThanOrEqualTo(SLO2_READINGS_PER_SECOND);
    } finally {
      simulationThread.shutdownNow();
      simulatorClient.disconnect();
      platformClient.disconnect();
    }
  }
}
