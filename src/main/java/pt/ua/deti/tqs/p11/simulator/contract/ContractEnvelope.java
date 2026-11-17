package pt.ua.deti.tqs.p11.simulator.contract;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The outer shape every message of the contract shares (contract/README.md, section Message
 * envelope): version, a new message id, type, UTC timestamp and identifiers, with the content in
 * {@code data}.
 */
final class ContractEnvelope {

  /** Contract version this simulator follows; must match contract/CHANGELOG.md. */
  static final String SCHEMA_VERSION = "1.0.0";

  private final Clock clock;
  private final Supplier<UUID> messageIds;

  ContractEnvelope(Clock clock, Supplier<UUID> messageIds) {
    this.clock = clock;
    this.messageIds = messageIds;
  }

  /** Wraps {@code data} in a new envelope, with the next message id and the current time. */
  Map<String, Object> wrap(
      String type, String fleetId, String vehicleId, Map<String, Object> data) {
    Map<String, Object> message = new LinkedHashMap<>();
    message.put("schemaVersion", SCHEMA_VERSION);
    message.put("messageId", messageIds.get().toString());
    message.put("type", type);
    // Milliseconds are enough and keep the timestamps short and comparable.
    message.put("timestamp", clock.instant().truncatedTo(ChronoUnit.MILLIS).toString());
    message.put("fleetId", fleetId);
    message.put("vehicleId", vehicleId);
    message.put("data", data);
    return message;
  }
}
