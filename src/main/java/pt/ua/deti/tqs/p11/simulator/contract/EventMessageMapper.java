package pt.ua.deti.tqs.p11.simulator.contract;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import pt.ua.deti.tqs.p11.simulator.event.FaultEvent;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns a fault event into an event message of the contract: the envelope with the identifiers of
 * the vehicle, and in {@code data} the event type, severity and fault code.
 */
public class EventMessageMapper {
  private final JsonMapper jsonMapper;
  private final ContractEnvelope envelope;

  public EventMessageMapper(JsonMapper jsonMapper, Clock clock, Supplier<UUID> messageIds) {
    this.jsonMapper = jsonMapper;
    this.envelope = new ContractEnvelope(clock, messageIds);
  }

  public String toJson(FaultEvent event) {
    // Required fields are always written; lockState is null because a fault is not a lock change.
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("eventType", event.type().name());
    data.put("severity", event.severity().name());
    data.put("faultCode", event.faultCode());
    data.put("lockState", null);

    return jsonMapper.writeValueAsString(
        envelope.wrap("event", event.fleetId(), event.vehicleId(), data));
  }
}
