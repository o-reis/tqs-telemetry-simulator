package pt.ua.deti.tqs.p11.simulator.mqtt;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.ua.deti.tqs.p11.simulator.command.VehicleCommand;
import pt.ua.deti.tqs.p11.simulator.contract.CommandMessageMapper;
import pt.ua.deti.tqs.p11.simulator.contract.ContractTopics;
import pt.ua.deti.tqs.p11.simulator.contract.InvalidCommandMessageException;

/**
 * Adapter: listens to the platform's commands for every vehicle of the fleet (QoS 1) and hands each
 * valid one on. A message that is not a valid command is logged and dropped: without a valid
 * commandId no response can be sent for it.
 */
public class MqttCommandSubscriber {

  private static final Logger LOG = LoggerFactory.getLogger(MqttCommandSubscriber.class);

  private final Mqtt5AsyncClient client;
  private final String fleetId;
  private final CommandMessageMapper mapper;
  private final Consumer<VehicleCommand> commands;

  /**
   * @param commands receives every valid command, on the MQTT client's thread; normally {@code
   *     CommandService::submit}
   */
  public MqttCommandSubscriber(
      Mqtt5AsyncClient client,
      String fleetId,
      CommandMessageMapper mapper,
      Consumer<VehicleCommand> commands) {
    this.client = client;
    this.fleetId = fleetId;
    this.mapper = mapper;
    this.commands = commands;
  }

  /**
   * Subscribes to the fleet's command topics.
   *
   * @return completes when the broker acknowledged the subscription
   */
  public CompletableFuture<Void> subscribe() {
    String filter = ContractTopics.commandsOfFleet(fleetId);
    CompletableFuture<Void> subscribed =
        client
            .subscribeWith()
            .topicFilter(filter)
            .qos(MqttQos.AT_LEAST_ONCE)
            .callback(
                message -> onMessage(message.getTopic().toString(), message.getPayloadAsBytes()))
            .send()
            .thenApply(ack -> null);
    subscribed.thenRun(() -> LOG.info("Listening for commands on {}", filter));
    subscribed.exceptionally(error -> {
      LOG.error("Could not subscribe to {}: {}", filter, error.getMessage());
      return null;
    });
    return subscribed;
  }

  /**
   * Handles one message from a command topic. Never throws: an exception thrown into the MQTT
   * client's callback could stop the subscription.
   */
  void onMessage(String topic, byte[] payload) {
    try {
      commands.accept(mapper.read(topic, new String(payload, StandardCharsets.UTF_8)));
    } catch (InvalidCommandMessageException e) {
      LOG.warn("Ignored a message on {}: {}", topic, e.getMessage());
    } catch (RuntimeException e) {
      LOG.error("Command on {} could not be handled", topic, e);
    }
  }
}
