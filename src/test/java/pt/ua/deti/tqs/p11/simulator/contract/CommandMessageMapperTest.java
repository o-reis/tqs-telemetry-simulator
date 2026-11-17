package pt.ua.deti.tqs.p11.simulator.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import pt.ua.deti.tqs.p11.simulator.command.CommandName;
import pt.ua.deti.tqs.p11.simulator.command.VehicleCommand;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads commands as the platform sends them. Anything that is not a valid command of the contract
 * is refused with a message that says why, so the adapter can log it and drop it.
 */
class CommandMessageMapperTest {
  private static final Path VALID_EXAMPLES = Path.of("contract", "examples", "valid");
  private static final Path INVALID_EXAMPLES = Path.of("contract", "examples", "invalid");

  private static final String COMMAND_ID = "e5b2a9c1-0d3f-4a8e-8b77-1c2d3e4f5a04";
  private static final String UNLOCK_TOPIC = "fleet/fleet-01/vehicles/bike-0042/commands/unlock";

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final CommandMessageMapper mapper = new CommandMessageMapper(JSON);

  /** A valid unlock command; each invalid case changes one thing in it. */
  private static Map<String, Object> validMessage() {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("commandId", COMMAND_ID);
    data.put("expiresAt", "2026-11-10T10:10:30Z");
    Map<String, Object> message = new LinkedHashMap<>();
    message.put("schemaVersion", "1.0.0");
    message.put("messageId", "c3a1f8d0-6a24-4f67-b1d2-9e3e4a5b6c03");
    message.put("type", "command");
    message.put("timestamp", "2026-11-10T10:10:00Z");
    message.put("fleetId", "fleet-01");
    message.put("vehicleId", "bike-0042");
    message.put("data", data);
    return message;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> dataOf(Map<String, Object> message) {
    return (Map<String, Object>) message.get("data");
  }

  private static String json(Map<String, Object> message) {
    return JSON.writeValueAsString(message);
  }

  private static String json(Consumer<Map<String, Object>> change) {
    Map<String, Object> message = validMessage();
    change.accept(message);
    return json(message);
  }

  @Test
  void read_validUnlock_returnsTheCommandWithTheIdentifiersOfTheTopic() {
    VehicleCommand command = mapper.read(UNLOCK_TOPIC, json(validMessage()));

    assertThat(command)
        .isEqualTo(
            new VehicleCommand(
                "fleet-01",
                "bike-0042",
                CommandName.UNLOCK,
                UUID.fromString(COMMAND_ID),
                Instant.parse("2026-11-10T10:10:30Z")));
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"lock", "unlock", "ping"})
  void read_everyValidExampleOfTheContract_isAccepted(String name) throws IOException {
    String example = Files.readString(VALID_EXAMPLES.resolve("command-" + name + ".json"));

    VehicleCommand command =
        mapper.read("fleet/fleet-01/vehicles/bike-0042/commands/" + name, example);

    assertThat(command.name().wireName()).isEqualTo(name);
  }

  @Test
  void read_withoutExpiresAt_hasNoDeadline() {
    VehicleCommand command =
        mapper.read(UNLOCK_TOPIC, json(message -> dataOf(message).remove("expiresAt")));

    assertThat(command.expiresAt()).isNull();
  }

  /** Tolerant reader: a newer MINOR version may add fields; they are ignored, not refused. */
  @Test
  void read_unknownFieldsAndANewerMinorVersion_areAccepted() {
    String newer =
        json(message -> {
          message.put("schemaVersion", "1.3.0");
          message.put("traceId", "abc");
          dataOf(message).put("priority", "high");
        });

    assertThat(mapper.read(UNLOCK_TOPIC, newer).commandId()).isEqualTo(UUID.fromString(COMMAND_ID));
  }

  static Stream<Arguments> invalidMessages() {
    return Stream.of(
        Arguments.of("not JSON", "{", "JSON"),
        Arguments.of("JSON but not an object", "[1, 2]", "object"),
        Arguments.of("no schemaVersion", json(m -> m.remove("schemaVersion")), "schemaVersion"),
        Arguments.of(
            "another major version", json(m -> m.put("schemaVersion", "2.0.0")), "schemaVersion"),
        Arguments.of("another type", json(m -> m.put("type", "telemetry")), "type"),
        Arguments.of(
            "fleetId differs from the topic", json(m -> m.put("fleetId", "fleet-02")), "topic"),
        Arguments.of(
            "vehicleId differs from the topic",
            json(m -> m.put("vehicleId", "bike-0043")),
            "topic"),
        Arguments.of("no data", json(m -> m.remove("data")), "data"),
        Arguments.of("no commandId", json(m -> dataOf(m).remove("commandId")), "commandId"),
        Arguments.of(
            "commandId not a UUID",
            json(m -> dataOf(m).put("commandId", "not-a-uuid")),
            "commandId"),
        Arguments.of(
            "commandId that only Java accepts as a UUID",
            json(m -> dataOf(m).put("commandId", "1-2-3-4-5")),
            "commandId"),
        Arguments.of(
            "expiresAt not a date", json(m -> dataOf(m).put("expiresAt", "soon")), "expiresAt"),
        Arguments.of(
            "expiresAt not in UTC",
            json(m -> dataOf(m).put("expiresAt", "2026-11-10T11:10:30+01:00")),
            "expiresAt"),
        Arguments.of(
            "commandId not text", json(m -> dataOf(m).put("commandId", 12345)), "commandId"),
        Arguments.of(
            "expiresAt not text", json(m -> dataOf(m).put("expiresAt", 1700000000)), "expiresAt"),
        Arguments.of(
            "expiresAt ends in Z but is not a date",
            json(m -> dataOf(m).put("expiresAt", "2026-13-45T10:10:30Z")),
            "expiresAt"),
        Arguments.of("empty payload", "", "JSON"),
        Arguments.of("data not an object", json(m -> m.put("data", "unlock")), "data"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidMessages")
  void read_invalidMessage_isRefusedWithTheReason(
      String description, String payload, String reasonMentions) {
    assertThatThrownBy(() -> mapper.read(UNLOCK_TOPIC, payload))
        .isInstanceOf(InvalidCommandMessageException.class)
        .hasMessageContaining(reasonMentions);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "fleet/fleet-01/vehicles/bike-0042/commands/reboot",
        "fleet/fleet-01/vehicles/bike-0042/telemetry",
        "fleet/fleet-01/vehicles/bike-0042/commands/unlock/extra"
      })
  void read_topicThatIsNotALockUnlockOrPingCommand_isRefused(String topic) {
    assertThatThrownBy(() -> mapper.read(topic, json(validMessage())))
        .isInstanceOf(InvalidCommandMessageException.class)
        .hasMessageContaining("topic");
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"command-command-id-not-uuid.json", "command-missing-command-id.json"})
  void read_everyInvalidCommandExampleOfTheContract_isRefused(String file) throws IOException {
    String example = Files.readString(INVALID_EXAMPLES.resolve(file));

    // Checked on the reason too, so a refusal for another cause (such as the topic) does not pass.
    assertThatThrownBy(() -> mapper.read(UNLOCK_TOPIC, example))
        .isInstanceOf(InvalidCommandMessageException.class)
        .hasMessageContaining("commandId");
  }
}
