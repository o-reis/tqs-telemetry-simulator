package pt.ua.deti.tqs.p11.simulator.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import pt.ua.deti.tqs.p11.simulator.contract.ContractSchemas;
import pt.ua.deti.tqs.p11.simulator.contract.TelemetryMessageMapper;
import pt.ua.deti.tqs.p11.simulator.telemetry.LockState;
import pt.ua.deti.tqs.p11.simulator.telemetry.VehicleTelemetry;
import tools.jackson.databind.json.JsonMapper;

/** The scenarios shipped with the simulator must stay valid and usable. */
class ExampleScenariosTest {

  private static final Path DIR = Path.of("src", "main", "resources", "scenarios");

  private final ScenarioLoader loader = new ScenarioLoader(new ScenarioParser());
  private final TelemetryMessageMapper mapper =
      new TelemetryMessageMapper(JsonMapper.builder().build(), Clock.systemUTC());

  static Stream<Path> exampleFiles() throws IOException {
    try (Stream<Path> files = Files.list(DIR)) {
      return files.filter(f -> f.toString().endsWith(".yml")).sorted().toList().stream();
    }
  }

  private ScenarioConfig load(Path file) throws IOException {
    try (InputStream input = Files.newInputStream(file)) {
      return loader.load(input, file.toString());
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("exampleFiles")
  void exampleScenario_isValid_andEveryVehicleBuildsAContractMessage(Path file) throws IOException {
    ScenarioConfig config = load(file);

    for (VehicleConfig vehicle : config.vehicles()) {
      VehicleTelemetry telemetry =
          new VehicleTelemetry(
              config.fleetId(),
              vehicle.id(),
              vehicle.battery(),
              LockState.LOCKED,
              null,
              vehicle.hubId(),
              null,
              null);

      assertThat(ContractSchemas.validateTelemetry(mapper.toJson(telemetry))).isEmpty();
    }
  }

  @Test
  void riaRide_hasAFleetWithBatteriesAroundTheBr1Boundary() throws IOException {
    ScenarioConfig config = load(DIR.resolve("ria-ride.yml"));

    assertThat(config.vehicles()).hasSizeGreaterThanOrEqualTo(20);
    assertThat(config.vehicles()).extracting(VehicleConfig::battery).contains(19.0, 20.0, 21.0);
    assertThat(config.vehicles())
        .extracting(VehicleConfig::type)
        .contains(VehicleType.E_BIKE, VehicleType.E_SCOOTER);
  }

  @Test
  void riaRide_hasHubsNextToHotels() throws IOException {
    ScenarioConfig config = load(DIR.resolve("ria-ride.yml"));

    assertThat(config.hubs())
        .extracting(HubConfig::name)
        .filteredOn(name -> name.startsWith("Hotel"))
        .hasSizeGreaterThanOrEqualTo(2);
  }

  @Test
  void minimal_hasOneHubAndOneVehicle() throws IOException {
    ScenarioConfig config = load(DIR.resolve("minimal.yml"));

    assertThat(config.hubs()).hasSize(1);
    assertThat(config.vehicles()).hasSize(1);
  }
}
