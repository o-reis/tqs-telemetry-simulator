package pt.ua.deti.tqs.p11.simulator.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pt.ua.deti.tqs.p11.simulator.event.FaultEvent;
import pt.ua.deti.tqs.p11.simulator.event.FaultEventType;
import pt.ua.deti.tqs.p11.simulator.event.Severity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class EventMessageMapperTest {
  private static final Instant NOW = Instant.parse("2026-11-10T10:05:12.987654Z");
  private static final UUID MESSAGE_ID = UUID.fromString("0b8e5a62-5d6e-4c0a-8d11-2f6c0b9f4a02");

  private static final FaultEvent RAISED =
      new FaultEvent(
          "fleet-01", "bike-0042", FaultEventType.FAULT_RAISED, "BRAKE_WEAR", Severity.WARNING);
  private static final FaultEvent CLEARED =
      new FaultEvent(
          "fleet-01", "bike-0042", FaultEventType.FAULT_CLEARED, "BRAKE_WEAR", Severity.WARNING);

  private final JsonMapper jsonMapper = JsonMapper.builder().build();
  private final EventMessageMapper mapper =
      new EventMessageMapper(jsonMapper, Clock.fixed(NOW, ZoneOffset.UTC), () -> MESSAGE_ID);

  @Test
  void raisedFault_followsTheEventSchema() {
    assertThat(ContractSchemas.validateEvent(mapper.toJson(RAISED))).isEmpty();
  }

  @Test
  void clearedFault_followsTheEventSchema() {
    assertThat(ContractSchemas.validateEvent(mapper.toJson(CLEARED))).isEmpty();
  }

  @Test
  void envelope_carriesVersionIdTypeTimestampAndTheIdentifiersOfTheEvent() {
    JsonNode message = jsonMapper.readTree(mapper.toJson(RAISED));

    assertThat(message.get("schemaVersion").asString()).isEqualTo("1.0.0");
    assertThat(message.get("messageId").asString()).isEqualTo(MESSAGE_ID.toString());
    assertThat(message.get("type").asString()).isEqualTo("event");
    assertThat(message.get("timestamp").asString()).isEqualTo("2026-11-10T10:05:12.987Z");
    assertThat(message.get("fleetId").asString()).isEqualTo("fleet-01");
    assertThat(message.get("vehicleId").asString()).isEqualTo("bike-0042");
  }

  @Test
  void raisedFault_carriesTheFaultCodeAndSeverityAndNoLockState() {
    JsonNode data = jsonMapper.readTree(mapper.toJson(RAISED)).get("data");

    assertThat(data.get("eventType").asString()).isEqualTo("FAULT_RAISED");
    assertThat(data.get("severity").asString()).isEqualTo("WARNING");
    assertThat(data.get("faultCode").asString()).isEqualTo("BRAKE_WEAR");
    assertThat(data.get("lockState").isNull()).isTrue();
  }

  @Test
  void clearedFault_isReportedWithTheCodeOfTheFaultThatWentAway() {
    JsonNode data = jsonMapper.readTree(mapper.toJson(CLEARED)).get("data");

    assertThat(data.get("eventType").asString()).isEqualTo("FAULT_CLEARED");
    assertThat(data.get("faultCode").asString()).isEqualTo("BRAKE_WEAR");
  }

  @Test
  void criticalSeverity_isWrittenAsCritical() {
    FaultEvent overheat =
        new FaultEvent(
            "fleet-01",
            "bike-0042",
            FaultEventType.FAULT_RAISED,
            "BATTERY_OVERHEAT",
            Severity.CRITICAL);

    JsonNode data = jsonMapper.readTree(mapper.toJson(overheat)).get("data");

    assertThat(data.get("severity").asString()).isEqualTo("CRITICAL");
  }
}
