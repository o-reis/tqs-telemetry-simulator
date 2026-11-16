package pt.ua.deti.tqs.p11.simulator.mqtt;

import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import java.time.Clock;
import java.util.random.RandomGenerator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pt.ua.deti.tqs.p11.simulator.contract.RandomUuids;
import pt.ua.deti.tqs.p11.simulator.contract.TelemetryMessageMapper;
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
   * and message ids are reproducible too.
   */
  @Bean
  TelemetryPublisher telemetryPublisher(
      Mqtt5AsyncClient mqttClient,
      JsonMapper jsonMapper,
      Clock clock,
      RandomGenerator simulationRandom) {
    return new MqttTelemetryPublisher(
        mqttClient,
        new TelemetryMessageMapper(jsonMapper, clock, new RandomUuids(simulationRandom)));
  }
}
