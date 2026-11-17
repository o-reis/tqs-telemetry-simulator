package pt.ua.deti.tqs.p11.simulator.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pt.ua.deti.tqs.p11.simulator.config.ScenarioConfig;
import pt.ua.deti.tqs.p11.simulator.config.ScenarioLoader;
import pt.ua.deti.tqs.p11.simulator.config.ScenarioParser;
import pt.ua.deti.tqs.p11.simulator.config.SimulationParams;
import pt.ua.deti.tqs.p11.simulator.engine.SimulationEngine;
import tools.jackson.databind.json.JsonMapper;

/**
 * Every message the simulator emits must follow the contract in contract/schemas. Runs apart from
 * the unit tests: {@code ./mvnw test -Pcontract}.
 */
@Tag("contract")
class TelemetryContractTest {

  private static final Path EXAMPLES = Path.of("contract", "examples");
  private static final Instant START = Instant.parse("2026-11-16T10:00:00.123Z");
  private static final int TICKS = 200;

  /**
   * The demo scenario (20 vehicles, batteries from 5 to 100 %) with a high fault probability, so
   * messages with and without a fault are both emitted.
   */
  private static ScenarioConfig scenario(long seed, double drain, double charge)
      throws IOException {
    try (var input = Files.newInputStream(Path.of("src/main/resources/scenarios/ria-ride.yml"))) {
      ScenarioConfig demo = new ScenarioLoader(new ScenarioParser()).load(input, "ria-ride.yml");
      SimulationParams params = demo.simulation();
      return new ScenarioConfig(
          demo.fleetId(),
          new SimulationParams(params.emissionIntervalSeconds(), seed, 0.05, drain, charge),
          demo.hubs(),
          demo.vehicles());
    }
  }

  private static List<String> emittedJson(long seed) throws IOException {
    return emittedJson(seed, 0, 0);
  }

  /** Runs the engine with the real contract mapper, wired as in the application. */
  private static List<String> emittedJson(long seed, double drain, double charge)
      throws IOException {
    Random random = new Random(seed);
    TelemetryMessageMapper mapper =
        new TelemetryMessageMapper(
            JsonMapper.builder().build(),
            Clock.fixed(START, ZoneOffset.UTC),
            new RandomUuids(random));
    List<String> out = new ArrayList<>();
    SimulationEngine engine =
        new SimulationEngine(
            scenario(seed, drain, charge),
            telemetry -> {
              out.add(mapper.toJson(telemetry));
              return CompletableFuture.completedFuture(null);
            },
            random);
    for (int i = 0; i < TICKS; i++) {
      engine.tick();
    }
    return out;
  }

  @ParameterizedTest
  @ValueSource(longs = {1, 42, 2026})
  void everyEmittedTelemetry_followsTheTelemetrySchema(long seed) throws IOException {
    List<String> messages = emittedJson(seed);

    assertThat(messages).hasSize(20 * TICKS);
    assertThat(messages).anyMatch(m -> m.contains("\"faultCode\":null"));
    assertThat(messages).anyMatch(m -> m.contains("\"faultCode\":\""));
    assertThat(messages)
        .allSatisfy(json -> assertThat(ContractSchemas.validateTelemetry(json)).as(json).isEmpty());
  }

  /**
   * With a charge rate every parked vehicle charges up to the cap. The scooter starts at 5 % and
   * gains 1 % per 10 s tick, so it reaches 100 % within the run, and no reading passes it.
   */
  @Test
  void everyEmittedTelemetry_withChargingVehicles_followsTheTelemetrySchema() throws IOException {
    List<String> messages = emittedJson(7, 0.5, 6.0);

    assertThat(messages).hasSize(20 * TICKS);
    assertThat(messages)
        .anyMatch(m ->
            m.contains("\"vehicleId\":\"scooter-0008\"") && m.contains("\"batteryPercent\":100.0"));
    assertThat(messages)
        .allSatisfy(json -> assertThat(ContractSchemas.validateTelemetry(json)).as(json).isEmpty());
  }

  /** Without this, a validator that accepts everything would make the test above pass. */
  @Test
  void emittedTelemetryWithABrokenField_isReported() throws IOException {
    String valid = emittedJson(1).get(0);
    String broken = valid.replaceFirst("\"batteryPercent\":[0-9.]+", "\"batteryPercent\":150");

    assertThat(broken).isNotEqualTo(valid);
    assertThat(ContractSchemas.validateTelemetry(broken)).isNotEmpty();
  }

  /** The Java validator must agree with the contract's own examples (checked by npm in CI too). */
  @Test
  void validator_acceptsEveryValidExample_andRejectsEveryInvalidOne() throws IOException {
    List<Path> valid = telemetryExamples("valid");
    List<Path> invalid = telemetryExamples("invalid");

    assertThat(valid).isNotEmpty();
    assertThat(invalid).isNotEmpty();
    assertThat(valid)
        .allSatisfy(f ->
            assertThat(ContractSchemas.validateTelemetry(Files.readString(f)))
                .as(f.toString())
                .isEmpty());
    assertThat(invalid)
        .allSatisfy(f ->
            assertThat(ContractSchemas.validateTelemetry(Files.readString(f)))
                .as(f.toString())
                .isNotEmpty());
  }

  private static List<Path> telemetryExamples(String kind) throws IOException {
    try (Stream<Path> files = Files.list(EXAMPLES.resolve(kind))) {
      return files
          .filter(f -> f.getFileName().toString().startsWith("telemetry-"))
          .sorted()
          .toList();
    }
  }
}
