package pt.ua.deti.tqs.p11.simulator.mqtt;

import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.Mqtt5ClientBuilder;
import com.hivemq.client.mqtt.mqtt5.message.auth.Mqtt5SimpleAuth;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Builds the MQTT client from the configuration. */
public final class MqttClientFactory {

  private MqttClientFactory() {}

  /**
   * Creates a client that is not connected yet. Automatic reconnect is on: when the broker is
   * unreachable or the connection drops, the client keeps retrying with growing delays (1 s up to 2
   * min) and resends the messages it could not deliver.
   */
  public static Mqtt5AsyncClient create(MqttProperties properties) {
    String clientId =
        properties.clientId().isBlank()
            ? "telemetry-simulator-" + UUID.randomUUID()
            : properties.clientId();
    Mqtt5ClientBuilder builder =
        Mqtt5Client.builder()
            .identifier(clientId)
            .serverHost(properties.host())
            .serverPort(properties.port())
            .automaticReconnectWithDefaultConfig();
    if (!properties.username().isBlank()) {
      builder.simpleAuth(
          Mqtt5SimpleAuth.builder()
              .username(properties.username())
              .password(properties.password().getBytes(StandardCharsets.UTF_8))
              .build());
    }
    return builder.buildAsync();
  }
}
