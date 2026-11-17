package pt.ua.deti.tqs.p11.simulator.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pt.ua.deti.tqs.p11.simulator.command.CommandName;
import pt.ua.deti.tqs.p11.simulator.command.VehicleCommand;
import pt.ua.deti.tqs.p11.simulator.engine.CommandOutcome;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ResponseMessageMapperTest {
  private static final Instant NOW = Instant.parse("2026-11-10T10:10:01.987654Z");
  private static final UUID MESSAGE_ID = UUID.fromString("1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c05");
  private static final UUID COMMAND_ID = UUID.fromString("e5b2a9c1-0d3f-4a8e-8b77-1c2d3e4f5a04");

  private static final VehicleCommand LOCK =
      new VehicleCommand("fleet-01", "bike-0042", CommandName.LOCK, COMMAND_ID, null);

  private final JsonMapper jsonMapper = JsonMapper.builder().build();
  private final ResponseMessageMapper mapper =
      new ResponseMessageMapper(jsonMapper, Clock.fixed(NOW, ZoneOffset.UTC), () -> MESSAGE_ID);

  @Test
  void completedResponse_followsTheResponseSchema() {
    String json = mapper.toJson(LOCK, CommandOutcome.completed());

    assertThat(ContractSchemas.validateResponse(json)).isEmpty();
  }

  @Test
  void rejectedResponse_followsTheResponseSchema() {
    String json = mapper.toJson(LOCK, CommandOutcome.rejected("NOT_AT_HUB"));

    assertThat(ContractSchemas.validateResponse(json)).isEmpty();
  }

  @Test
  void envelope_carriesVersionIdTypeTimestampAndTheIdentifiersOfTheCommand() {
    JsonNode message = jsonMapper.readTree(mapper.toJson(LOCK, CommandOutcome.completed()));

    assertThat(message.get("schemaVersion").asString()).isEqualTo("1.0.0");
    assertThat(message.get("messageId").asString()).isEqualTo(MESSAGE_ID.toString());
    assertThat(message.get("type").asString()).isEqualTo("response");
    assertThat(message.get("timestamp").asString()).isEqualTo("2026-11-10T10:10:01.987Z");
    assertThat(message.get("fleetId").asString()).isEqualTo("fleet-01");
    assertThat(message.get("vehicleId").asString()).isEqualTo("bike-0042");
  }

  @Test
  void completed_echoesTheCommandWithANullReason() {
    JsonNode data =
        jsonMapper.readTree(mapper.toJson(LOCK, CommandOutcome.completed())).get("data");

    assertThat(data.get("commandId").asString()).isEqualTo(COMMAND_ID.toString());
    assertThat(data.get("command").asString()).isEqualTo("lock");
    assertThat(data.get("status").asString()).isEqualTo("COMPLETED");
    assertThat(data.get("reason").isNull()).isTrue();
  }

  @Test
  void rejected_carriesTheStatusAndTheReason() {
    JsonNode data =
        jsonMapper.readTree(mapper.toJson(LOCK, CommandOutcome.rejected("NOT_AT_HUB"))).get("data");

    assertThat(data.get("status").asString()).isEqualTo("REJECTED");
    assertThat(data.get("reason").asString()).isEqualTo("NOT_AT_HUB");
  }
}
