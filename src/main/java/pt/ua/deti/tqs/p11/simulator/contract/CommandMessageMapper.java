package pt.ua.deti.tqs.p11.simulator.contract;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import pt.ua.deti.tqs.p11.simulator.command.CommandName;
import pt.ua.deti.tqs.p11.simulator.command.VehicleCommand;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeType;
import tools.jackson.databind.node.ObjectNode;

/**
 * Reads a command of the contract from its MQTT topic and JSON payload. The command name and the
 * vehicle come from the topic; the payload must agree with the topic. Following the tolerant reader
 * rule, unknown fields and newer MINOR versions are accepted.
 */
public class CommandMessageMapper {

  /** {@code fleet/{fleetId}/vehicles/{vehicleId}/commands/{name}}; identifiers never hold '/'. */
  private static final Pattern COMMAND_TOPIC =
      Pattern.compile("fleet/([^/]+)/vehicles/([^/]+)/commands/([^/]+)");

  /**
   * Canonical UUID text, as the schema's "uuid" format requires. {@link UUID#fromString} alone also
   * accepts shortened forms such as "1-2-3-4-5".
   */
  private static final Pattern UUID_TEXT =
      Pattern.compile(
          "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

  /** Semantic version whose MAJOR is 1, the only major version this simulator supports. */
  private static final Pattern VERSION_1 = Pattern.compile("1\\.\\d+\\.\\d+");

  private final JsonMapper jsonMapper;

  public CommandMessageMapper(JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
  }

  /**
   * @throws InvalidCommandMessageException if the topic or the payload is not a valid command; the
   *     message says why
   */
  public VehicleCommand read(String topic, String payload) {
    Matcher topicParts = COMMAND_TOPIC.matcher(topic);
    if (!topicParts.matches()) {
      throw invalid("not a command topic: " + topic);
    }
    CommandName name =
        CommandName.fromWireName(topicParts.group(3))
            .orElseThrow(() -> invalid("unknown command in topic: " + topic));

    JsonNode message = parseObject(payload);
    String version = text(message, "schemaVersion");
    if (version == null || !VERSION_1.matcher(version).matches()) {
      throw invalid("schemaVersion must be 1.x.y (was " + version + ")");
    }
    if (!"command".equals(text(message, "type"))) {
      throw invalid("type must be 'command' (was " + text(message, "type") + ")");
    }
    JsonNode data = message.get("data");
    if (data == null || !data.isObject()) {
      throw invalid("data must be an object");
    }
    UUID commandId = commandId(data);
    Instant expiresAt = expiresAt(data);

    String fleetId = topicParts.group(1);
    String vehicleId = topicParts.group(2);
    if (!fleetId.equals(text(message, "fleetId"))
        || !vehicleId.equals(text(message, "vehicleId"))) {
      throw invalid("fleetId and vehicleId must match the topic " + topic);
    }
    return new VehicleCommand(fleetId, vehicleId, name, commandId, expiresAt);
  }

  private JsonNode parseObject(String payload) {
    JsonNode node;
    try {
      node = jsonMapper.readTree(payload);
    } catch (JacksonException e) {
      throw invalid("payload is not valid JSON");
    }
    if (!(node instanceof ObjectNode)) {
      throw invalid("payload must be a JSON object");
    }
    return node;
  }

  private static UUID commandId(JsonNode data) {
    String commandId = text(data, "commandId");
    if (commandId == null || !UUID_TEXT.matcher(commandId).matches()) {
      throw invalid("data.commandId must be a UUID (was " + commandId + ")");
    }
    return UUID.fromString(commandId);
  }

  /** Absent means no deadline. A present value must be a UTC date-time ending in 'Z'. */
  private static Instant expiresAt(JsonNode data) {
    if (!data.has("expiresAt")) {
      return null;
    }
    String expiresAt = text(data, "expiresAt");
    // Instant.parse also accepts offsets such as +01:00; the contract only allows UTC.
    if (expiresAt == null || !expiresAt.endsWith("Z")) {
      throw invalid("data.expiresAt must be a UTC date-time ending in Z (was " + expiresAt + ")");
    }
    try {
      return Instant.parse(expiresAt);
    } catch (DateTimeParseException e) {
      throw invalid("data.expiresAt must be a UTC date-time ending in Z (was " + expiresAt + ")");
    }
  }

  /** The text of a field, or {@code null} when it is absent or not a string. */
  private static String text(JsonNode parent, String field) {
    JsonNode value = parent.get(field);
    return value != null && value.getNodeType() == JsonNodeType.STRING ? value.asString() : null;
  }

  private static InvalidCommandMessageException invalid(String reason) {
    return new InvalidCommandMessageException(reason);
  }
}
