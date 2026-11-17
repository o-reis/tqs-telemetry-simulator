package pt.ua.deti.tqs.p11.simulator.contract;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import pt.ua.deti.tqs.p11.simulator.command.VehicleCommand;
import pt.ua.deti.tqs.p11.simulator.engine.CommandOutcome;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns the outcome of a command into a response message of the contract: the envelope with the
 * command's identifiers, and in {@code data} the command id and name, the status and the reason.
 */
public class ResponseMessageMapper {

  private final JsonMapper jsonMapper;
  private final Clock clock;
  private final Supplier<UUID> messageIds;

  public ResponseMessageMapper(JsonMapper jsonMapper, Clock clock, Supplier<UUID> messageIds) {
    this.jsonMapper = jsonMapper;
    this.clock = clock;
    this.messageIds = messageIds;
  }

  public String toJson(VehicleCommand command, CommandOutcome outcome) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("commandId", command.commandId().toString());
    data.put("command", command.name().wireName());
    data.put("status", outcome.status().name());
    data.put("reason", outcome.reason());

    Map<String, Object> message = new LinkedHashMap<>();
    message.put("schemaVersion", TelemetryMessageMapper.SCHEMA_VERSION);
    message.put("messageId", messageIds.get().toString());
    message.put("type", "response");
    message.put("timestamp", clock.instant().truncatedTo(ChronoUnit.MILLIS).toString());
    message.put("fleetId", command.fleetId());
    message.put("vehicleId", command.vehicleId());
    message.put("data", data);
    return jsonMapper.writeValueAsString(message);
  }
}
