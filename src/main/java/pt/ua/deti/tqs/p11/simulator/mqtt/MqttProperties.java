package pt.ua.deti.tqs.p11.simulator.mqtt;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Broker connection, set with environment variables: SIMULATOR_MQTT_HOST, SIMULATOR_MQTT_PORT,
 * SIMULATOR_MQTT_USERNAME, SIMULATOR_MQTT_PASSWORD and SIMULATOR_MQTT_CLIENT_ID.
 *
 * @param username empty to connect without authentication
 * @param clientId empty to generate a unique one at start-up
 */
@ConfigurationProperties("simulator.mqtt")
public record MqttProperties(
    @DefaultValue("localhost") String host,
    @DefaultValue("1883") int port,
    @DefaultValue("") String username,
    @DefaultValue("") String password,
    @DefaultValue("") String clientId) {}
