package pt.ua.deti.tqs.p11.simulator.mqtt;

import static org.assertj.core.api.Assertions.assertThat;

import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MqttClientFactoryTest {

  @Test
  void create_usesTheConfiguredBroker() {
    Mqtt5AsyncClient client =
        MqttClientFactory.create(new MqttProperties("broker.test", 1884, "", "", "sim-1"));

    assertThat(client.getConfig().getServerHost()).isEqualTo("broker.test");
    assertThat(client.getConfig().getServerPort()).isEqualTo(1884);
    assertThat(client.getConfig().getClientIdentifier())
        .hasValueSatisfying(id -> assertThat(id.toString()).isEqualTo("sim-1"));
  }

  @Test
  void create_reconnectsAutomatically() {
    Mqtt5AsyncClient client =
        MqttClientFactory.create(new MqttProperties("localhost", 1883, "", "", "sim-1"));

    assertThat(client.getConfig().getAutomaticReconnect()).isPresent();
  }

  @Test
  void create_withUsername_authenticatesWithUsernameAndPassword() {
    Mqtt5AsyncClient client =
        MqttClientFactory.create(new MqttProperties("localhost", 1883, "sim", "secret", "sim-1"));

    assertThat(client.getConfig().getSimpleAuth())
        .hasValueSatisfying(auth -> {
          assertThat(auth.getUsername())
              .hasValueSatisfying(username -> assertThat(username.toString()).isEqualTo("sim"));
          assertThat(auth.getPassword())
              .hasValueSatisfying(password ->
                  assertThat(StandardCharsets.UTF_8.decode(password).toString())
                      .isEqualTo("secret"));
        });
  }

  @Test
  void create_withoutUsername_connectsAnonymously() {
    Mqtt5AsyncClient client =
        MqttClientFactory.create(new MqttProperties("localhost", 1883, "", "secret", "sim-1"));

    assertThat(client.getConfig().getSimpleAuth()).isEmpty();
  }

  @Test
  void create_withoutClientId_generatesOne() {
    Mqtt5AsyncClient client =
        MqttClientFactory.create(new MqttProperties("localhost", 1883, "", "", ""));

    assertThat(client.getConfig().getClientIdentifier())
        .hasValueSatisfying(id -> assertThat(id.toString()).startsWith("telemetry-simulator-"));
  }
}
