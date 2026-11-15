package pt.ua.deti.tqs.p11.simulator.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pt.ua.deti.tqs.p11.simulator.telemetry.LockState;
import pt.ua.deti.tqs.p11.simulator.telemetry.Position;
import pt.ua.deti.tqs.p11.simulator.telemetry.VehicleTelemetry;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class TelemetryMessageMapperTest {

  private static final Instant NOW = Instant.parse("2026-11-10T10:00:00.123456Z");
  private static final UUID MESSAGE_ID = UUID.fromString("7d9f2c4e-3b1a-4e55-9a60-0c1f8f6b1a01");

  private final JsonMapper jsonMapper = JsonMapper.builder().build();
  private final TelemetryMessageMapper mapper =
      new TelemetryMessageMapper(jsonMapper, Clock.fixed(NOW, ZoneOffset.UTC), () -> MESSAGE_ID);

  private static final VehicleTelemetry PARKED =
      new VehicleTelemetry(
          "fleet-01", "bike-0042", 78.5, LockState.LOCKED, null, "hub-03", null, null);

  private static final VehicleTelemetry MOVING_WITH_FAULT =
      new VehicleTelemetry(
          "fleet-01",
          "scooter-0007",
          12,
          LockState.UNLOCKED,
          new Position(40.6405, -8.6538),
          null,
          "BRAKE_WEAR",
          18.0);

  @Test
  void parkedVehicle_followsTheTelemetrySchema() {
    assertThat(ContractSchemas.validateTelemetry(mapper.toJson(PARKED))).isEmpty();
  }

  @Test
  void movingVehicleWithFault_followsTheTelemetrySchema() {
    assertThat(ContractSchemas.validateTelemetry(mapper.toJson(MOVING_WITH_FAULT))).isEmpty();
  }

  @Test
  void envelope_carriesVersionIdTypeTimestampAndIdentifiers() {
    JsonNode message = jsonMapper.readTree(mapper.toJson(PARKED));

    assertThat(message.get("schemaVersion").asString()).isEqualTo("1.0.0");
    assertThat(message.get("messageId").asString()).isEqualTo(MESSAGE_ID.toString());
    assertThat(message.get("type").asString()).isEqualTo("telemetry");
    assertThat(message.get("timestamp").asString()).isEqualTo("2026-11-10T10:00:00.123Z");
    assertThat(message.get("fleetId").asString()).isEqualTo("fleet-01");
    assertThat(message.get("vehicleId").asString()).isEqualTo("bike-0042");
  }

  @Test
  void data_carriesTheVehicleState() {
    JsonNode data = jsonMapper.readTree(mapper.toJson(MOVING_WITH_FAULT)).get("data");

    assertThat(data.get("batteryPercent").asDouble()).isEqualTo(12.0);
    assertThat(data.get("lockState").asString()).isEqualTo("unlocked");
    assertThat(data.get("position").get("lat").asDouble()).isEqualTo(40.6405);
    assertThat(data.get("position").get("lon").asDouble()).isEqualTo(-8.6538);
    assertThat(data.get("faultCode").asString()).isEqualTo("BRAKE_WEAR");
    assertThat(data.get("speedKmh").asDouble()).isEqualTo(18.0);
  }

  @Test
  void unknownValues_areWrittenAsNull_andSpeedIsLeftOut() {
    JsonNode data = jsonMapper.readTree(mapper.toJson(PARKED)).get("data");

    assertThat(data.get("position").isNull()).isTrue();
    assertThat(data.get("faultCode").isNull()).isTrue();
    assertThat(data.get("hubId").asString()).isEqualTo("hub-03");
    assertThat(data.has("speedKmh")).isFalse();
  }
}
