package pt.ua.deti.tqs.p11.simulator.mqtt;

import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import java.time.Clock;
import java.util.random.RandomGenerator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pt.ua.deti.tqs.p11.simulator.command.CommandService;
import pt.ua.deti.tqs.p11.simulator.command.ResponsePublisher;
import pt.ua.deti.tqs.p11.simulator.config.ScenarioConfig;
import pt.ua.deti.tqs.p11.simulator.contract.CommandMessageMapper;
import pt.ua.deti.tqs.p11.simulator.contract.EventMessageMapper;
import pt.ua.deti.tqs.p11.simulator.contract.RandomUuids;
import pt.ua.deti.tqs.p11.simulator.contract.ResponseMessageMapper;
import pt.ua.deti.tqs.p11.simulator.contract.TelemetryMessageMapper;
import pt.ua.deti.tqs.p11.simulator.event.EventPublisher;
import pt.ua.deti.tqs.p11.simulator.rate.EmissionRateMeter;
import pt.ua.deti.tqs.p11.simulator.rate.MeteredTelemetryPublisher;
import pt.ua.deti.tqs.p11.simulator.telemetry.TelemetryPublisher;
import tools.jackson.databind.json.JsonMapper;

/** Wires the MQTT adapter. The client connects in the background, so start-up never blocks. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MqttProperties.class)
public class MqttConfiguration {

  @Bean(destroyMethod = "disconnect")
  Mqtt5AsyncClient mqttClient(MqttProperties properties) {
    Mqtt5AsyncClient client = MqttClientFactory.create(properties);
    client.connect();
    return client;
  }

  /**
   * The mapper takes the run's clock and seeded generator (SimulationConfiguration), so timestamps
   * and message ids are reproducible too. Every reading is counted on its way to the broker, for
   * the effective emission rate.
   */
  @Bean
  TelemetryPublisher telemetryPublisher(
      Mqtt5AsyncClient mqttClient,
      JsonMapper jsonMapper,
      Clock clock,
      RandomGenerator simulationRandom,
      EmissionRateMeter emissionRateMeter) {
    return new MeteredTelemetryPublisher(
        new MqttTelemetryPublisher(
            mqttClient,
            new TelemetryMessageMapper(jsonMapper, clock, new RandomUuids(simulationRandom))),
        emissionRateMeter);
  }

  /** Like the telemetry: the run's clock and seeded generator, so message ids are reproducible. */
  @Bean
  EventPublisher eventPublisher(
      Mqtt5AsyncClient mqttClient,
      JsonMapper jsonMapper,
      Clock clock,
      RandomGenerator simulationRandom) {
    return new MqttEventPublisher(
        mqttClient, new EventMessageMapper(jsonMapper, clock, new RandomUuids(simulationRandom)));
  }

  /** Like the telemetry: the run's clock and seeded generator, so message ids are reproducible. */
  @Bean
  ResponsePublisher responsePublisher(
      Mqtt5AsyncClient mqttClient,
      JsonMapper jsonMapper,
      Clock clock,
      RandomGenerator simulationRandom) {
    return new MqttResponsePublisher(
        mqttClient,
        new ResponseMessageMapper(jsonMapper, clock, new RandomUuids(simulationRandom)));
  }

  /** Subscribes in the background, like the connection, so start-up never waits for the broker. */
  @Bean
  MqttCommandSubscriber mqttCommandSubscriber(
      Mqtt5AsyncClient mqttClient,
      ScenarioConfig scenario,
      JsonMapper jsonMapper,
      CommandService commandService) {
    MqttCommandSubscriber subscriber =
        new MqttCommandSubscriber(
            mqttClient,
            scenario.fleetId(),
            new CommandMessageMapper(jsonMapper),
            commandService::submit);
    subscriber.subscribe();
    return subscriber;
  }
}
