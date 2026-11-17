package pt.ua.deti.tqs.p11.simulator.contract;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import pt.ua.deti.tqs.p11.simulator.telemetry.VehicleTelemetry;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns a vehicle state into a telemetry message of the contract: the envelope (version, message
 * id, type, UTC timestamp, identifiers) with the state in {@code data}.
 */
public class TelemetryMessageMapper {
  /** Contract version this simulator follows; must match contract/CHANGELOG.md. */
  public static final String SCHEMA_VERSION = ContractEnvelope.SCHEMA_VERSION;

  private final JsonMapper jsonMapper;
  private final ContractEnvelope envelope;

  public TelemetryMessageMapper(JsonMapper jsonMapper, Clock clock) {
    this(jsonMapper, clock, UUID::randomUUID);
  }

  public TelemetryMessageMapper(JsonMapper jsonMapper, Clock clock, Supplier<UUID> messageIds) {
    this.jsonMapper = jsonMapper;
    this.envelope = new ContractEnvelope(clock, messageIds);
  }

  public String toJson(VehicleTelemetry telemetry) {
    // Required fields are always written, as null when unknown; optional ones are left out.
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("batteryPercent", telemetry.batteryPercent());
    data.put("lockState", telemetry.lockState().name().toLowerCase(Locale.ROOT));
    data.put(
        "position",
        telemetry.position().map(p -> Map.of("lat", p.lat(), "lon", p.lon())).orElse(null));
    data.put("hubId", telemetry.hubId().orElse(null));
    data.put("faultCode", telemetry.faultCode().orElse(null));
    telemetry.speedKmh().ifPresent(speed -> data.put("speedKmh", speed));

    return jsonMapper.writeValueAsString(
        envelope.wrap("telemetry", telemetry.fleetId(), telemetry.vehicleId(), data));
  }
}
