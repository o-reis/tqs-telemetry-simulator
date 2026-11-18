package pt.ua.deti.tqs.p11.simulator.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import pt.ua.deti.tqs.p11.simulator.contract.ContractSchemas;
import pt.ua.deti.tqs.p11.simulator.contract.TelemetryMessageMapper;
import pt.ua.deti.tqs.p11.simulator.engine.SimulationEngine;
import pt.ua.deti.tqs.p11.simulator.rate.EmissionRateMeter;
import pt.ua.deti.tqs.p11.simulator.telemetry.LockState;
import pt.ua.deti.tqs.p11.simulator.telemetry.VehicleTelemetry;
import tools.jackson.databind.json.JsonMapper;

/** The scenarios shipped with the simulator must stay valid and usable. */
class ExampleScenariosTest {

  private static final Path DIR = Path.of("src", "main", "resources", "scenarios");

  /** Starting point for other teams' own scenarios, mounted into the container (README). */
  private static final Path TEMPLATE = Path.of("examples", "scenario.yml");

  private final ScenarioLoader loader = new ScenarioLoader(new ScenarioParser());
  private final TelemetryMessageMapper mapper =
      new TelemetryMessageMapper(JsonMapper.builder().build(), Clock.systemUTC());

  static Stream<Path> exampleFiles() throws IOException {
    try (Stream<Path> files = Files.list(DIR)) {
      return Stream.concat(
          files.filter(f -> f.toString().endsWith(".yml")).sorted().toList().stream(),
          Stream.of(TEMPLATE));
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
  void riaRideCharging_isTheSameFleetAsRiaRide_withAChargeRate() throws IOException {
    ScenarioConfig demo = load(DIR.resolve("ria-ride.yml"));
    ScenarioConfig charging = load(DIR.resolve("ria-ride-charging.yml"));

    assertThat(charging.hubs()).isEqualTo(demo.hubs());
    assertThat(charging.vehicles())
        .extracting(VehicleConfig::id)
        .containsExactlyElementsOf(demo.vehicles().stream().map(VehicleConfig::id).toList());
    assertThat(charging.simulation().chargePercentPerMinute()).isPositive();
  }

  /** The demo must show charging: the emptiest vehicle fills up within a short run. */
  @Test
  void riaRideCharging_bringsTheEmptiestVehicleToFullWithinTwentyFiveReadings() throws IOException {
    ScenarioConfig config = load(DIR.resolve("ria-ride-charging.yml"));
    Map<String, Double> lastBattery = new HashMap<>();
    SimulationEngine engine =
        new SimulationEngine(
            config,
            telemetry -> {
              lastBattery.put(telemetry.vehicleId(), telemetry.batteryPercent());
              return CompletableFuture.completedFuture(null);
            },
            new Random(config.simulation().seed()));

    for (int i = 0; i < 25; i++) {
      engine.tick();
    }

    assertThat(lastBattery).containsEntry("scooter-0008", 100.0);
  }

  @Test
  void riaRideTrips_isTheSameFleetAsRiaRide_withTrips() throws IOException {
    ScenarioConfig demo = load(DIR.resolve("ria-ride.yml"));
    ScenarioConfig trips = load(DIR.resolve("ria-ride-trips.yml"));

    assertThat(trips.hubs()).isEqualTo(demo.hubs());
    assertThat(trips.vehicles()).isEqualTo(demo.vehicles());
    assertThat(trips.simulation().tripProbability()).isPositive();
  }

  /** The demo must show movement: a vehicle reaches another hub within five minutes. */
  @Test
  void riaRideTrips_bringsAVehicleToAnotherHubWithinThirtyReadings() throws IOException {
    ScenarioConfig config = load(DIR.resolve("ria-ride-trips.yml"));
    Map<String, String> startHub =
        config.vehicles().stream()
            .collect(Collectors.toMap(VehicleConfig::id, VehicleConfig::hubId));

    List<VehicleTelemetry> readings = run(config, 30);

    assertThat(readings).anySatisfy(t -> assertThat(t.lockState()).isEqualTo(LockState.UNLOCKED));
    assertThat(readings)
        .anySatisfy(
            t -> assertThat(t.hubId()).isPresent().get().isNotEqualTo(startHub.get(t.vehicleId())));
  }

  /** Riding vehicles are in no hub: those messages must follow the contract too. */
  @Test
  void riaRideTrips_everyMessageOfARunFollowsTheContract() throws IOException {
    List<VehicleTelemetry> readings = run(load(DIR.resolve("ria-ride-trips.yml")), 60);

    assertThat(readings).anySatisfy(t -> assertThat(t.lockState()).isEqualTo(LockState.UNLOCKED));
    assertThat(readings)
        .allSatisfy(t -> assertThat(ContractSchemas.validateTelemetry(mapper.toJson(t))).isEmpty());
  }

  private static List<VehicleTelemetry> run(ScenarioConfig config, int ticks) {
    List<VehicleTelemetry> readings = new ArrayList<>();
    SimulationEngine engine =
        new SimulationEngine(
            config,
            telemetry -> {
              readings.add(telemetry);
              return CompletableFuture.completedFuture(null);
            },
            new Random(config.simulation().seed()));
    for (int i = 0; i < ticks; i++) {
      engine.tick();
    }
    return readings;
  }

  @Test
  void riaRide_hasHubsNextToHotels() throws IOException {
    ScenarioConfig config = load(DIR.resolve("ria-ride.yml"));

    assertThat(config.hubs())
        .extracting(HubConfig::name)
        .filteredOn(name -> name.startsWith("Hotel"))
        .hasSizeGreaterThanOrEqualTo(2);
  }

  /** A template is only useful if it shows the optional sections, not only the required ones. */
  @Test
  void template_showsScriptedFaultsAndGeneratedVehicles() throws IOException {
    ScenarioConfig config = load(TEMPLATE);

    assertThat(config.faults()).isNotEmpty();
    assertThat(config.vehicles()).extracting(VehicleConfig::id).contains("vehicle-0001");
    assertThat(config.simulation().tripProbability()).isPositive();
  }

  @Test
  void minimal_hasOneHubAndOneVehicle() throws IOException {
    ScenarioConfig config = load(DIR.resolve("minimal.yml"));

    assertThat(config.hubs()).hasSize(1);
    assertThat(config.vehicles()).hasSize(1);
  }

  // ---- The load test fleet (high-rate mode) ----

  /** SLO2 (Product Specification): telemetry lag under 5 s at 10 readings/s. */
  private static final double SLO2_READINGS_PER_SECOND = 10;

  @Test
  void loadTest_targetsAtLeastTenTimesTheTelemetrySloRate() throws IOException {
    ScenarioConfig config = load(DIR.resolve("load-test.yml"));

    double target = EmissionRateMeter.forScenario(Clock.systemUTC(), config).targetPerSecond();

    assertThat(config.vehicles()).hasSize(1000);
    assertThat(target).isGreaterThanOrEqualTo(10 * SLO2_READINGS_PER_SECOND);
  }

  @Test
  void loadTest_usesTheHubsOfRiaRide() throws IOException {
    ScenarioConfig demo = load(DIR.resolve("ria-ride.yml"));
    ScenarioConfig loadTest = load(DIR.resolve("load-test.yml"));

    assertThat(loadTest.hubs()).isEqualTo(demo.hubs());
  }
}
