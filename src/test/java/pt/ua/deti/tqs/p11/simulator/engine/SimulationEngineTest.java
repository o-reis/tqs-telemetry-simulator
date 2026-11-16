package pt.ua.deti.tqs.p11.simulator.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import pt.ua.deti.tqs.p11.simulator.config.HubConfig;
import pt.ua.deti.tqs.p11.simulator.config.ScenarioConfig;
import pt.ua.deti.tqs.p11.simulator.config.SimulationParams;
import pt.ua.deti.tqs.p11.simulator.config.VehicleConfig;
import pt.ua.deti.tqs.p11.simulator.config.VehicleType;
import pt.ua.deti.tqs.p11.simulator.contract.RandomUuids;
import pt.ua.deti.tqs.p11.simulator.contract.TelemetryMessageMapper;
import pt.ua.deti.tqs.p11.simulator.telemetry.LockState;
import pt.ua.deti.tqs.p11.simulator.telemetry.Position;
import pt.ua.deti.tqs.p11.simulator.telemetry.VehicleTelemetry;
import tools.jackson.databind.json.JsonMapper;

/** No broker and no real time: the publisher is a list and the engine is driven by tick(). */
class SimulationEngineTest {

  private static final Position HUB_01 = new Position(40.6443, -8.6455);
  private static final Position HUB_02 = new Position(40.6412, -8.6501);

  private final List<VehicleTelemetry> published = new ArrayList<>();

  private static ScenarioConfig scenario(long seed, double faultProbability) {
    return new ScenarioConfig(
        "fleet-01",
        new SimulationParams(10, seed, faultProbability),
        List.of(
            new HubConfig("hub-01", "Hub one", HUB_01), new HubConfig("hub-02", "Hub two", HUB_02)),
        List.of(
            new VehicleConfig("bike-0001", VehicleType.E_BIKE, "hub-01", 90),
            new VehicleConfig("scooter-0001", VehicleType.E_SCOOTER, "hub-02", 20),
            new VehicleConfig("bike-0002", VehicleType.E_BIKE, "hub-02", 19)));
  }

  private SimulationEngine engine(ScenarioConfig scenario) {
    return new SimulationEngine(scenario, this::record, new Random(scenario.simulation().seed()));
  }

  private CompletableFuture<Void> record(VehicleTelemetry telemetry) {
    published.add(telemetry);
    return CompletableFuture.completedFuture(null);
  }

  private static void tick(SimulationEngine engine, int times) {
    for (int i = 0; i < times; i++) {
      engine.tick();
    }
  }

  @Test
  void tick_publishesOneReadingPerVehicle_inScenarioOrder() {
    SimulationEngine engine = engine(scenario(1, 0));

    engine.tick();

    assertThat(published)
        .extracting(VehicleTelemetry::vehicleId)
        .containsExactly("bike-0001", "scooter-0001", "bike-0002");
  }

  @Test
  void everyTick_publishesForEveryVehicleAgain() {
    tick(engine(scenario(1, 0)), 4);

    assertThat(published).hasSize(12);
    assertThat(published.stream().collect(Collectors.groupingBy(VehicleTelemetry::vehicleId)))
        .allSatisfy((vehicle, readings) -> assertThat(readings).hasSize(4));
  }

  @Test
  void parkedVehicle_reportsFleetHubPositionLockAndBatteryFromTheScenario() {
    engine(scenario(1, 0)).tick();

    assertThat(published).hasSize(3);
    VehicleTelemetry scooter = published.get(1);
    assertThat(scooter.fleetId()).isEqualTo("fleet-01");
    assertThat(scooter.hubId()).contains("hub-02");
    assertThat(scooter.position()).contains(HUB_02);
    assertThat(scooter.lockState()).isEqualTo(LockState.LOCKED);
    assertThat(scooter.batteryPercent()).isEqualTo(20);
    assertThat(scooter.speedKmh()).contains(0.0);
  }

  @Test
  void faultProbabilityZero_neverRaisesAFault() {
    tick(engine(scenario(7, 0)), 200);

    assertThat(published).isNotEmpty().allSatisfy(t -> assertThat(t.faultCode()).isEmpty());
  }

  @Test
  void faultProbabilityOne_raisesAKnownFaultOnTheFirstTick() {
    engine(scenario(7, 1)).tick();

    assertThat(published)
        .hasSize(3)
        .allSatisfy(
            t -> assertThat(t.faultCode()).isPresent().get().isIn(SimulationEngine.FAULT_CODES));
  }

  @Test
  void raisedFault_staysTheSameOnLaterTicks() {
    tick(engine(scenario(7, 1)), 30);

    Map<String, List<Optional<String>>> faultsByVehicle =
        published.stream()
            .collect(
                Collectors.groupingBy(
                    VehicleTelemetry::vehicleId,
                    Collectors.mapping(VehicleTelemetry::faultCode, Collectors.toList())));
    assertThat(faultsByVehicle)
        .hasSize(3)
        .allSatisfy(
            (vehicle, faults) -> assertThat(faults).hasSize(30).containsOnly(faults.get(0)));
  }

  @Test
  void faultsAppearOverTime_withAProbabilityBetweenZeroAndOne() {
    tick(engine(scenario(3, 0.1)), 100);

    long healthy = published.stream().filter(t -> t.faultCode().isEmpty()).count();
    assertThat(healthy).isPositive().isLessThan(published.size());
  }

  @Test
  void sameSeedAndScenario_publishTheSameSequenceOfReadings() {
    List<VehicleTelemetry> first = run(scenario(42, 0.2), 50);
    List<VehicleTelemetry> second = run(scenario(42, 0.2), 50);

    assertThat(first).hasSize(150).usingRecursiveFieldByFieldElementComparator().isEqualTo(second);
  }

  @Test
  void differentSeeds_publishDifferentReadings() {
    List<VehicleTelemetry> first = run(scenario(1, 0.2), 50);
    List<VehicleTelemetry> second = run(scenario(2, 0.2), 50);

    assertThat(first).usingRecursiveFieldByFieldElementComparator().isNotEqualTo(second);
  }

  /**
   * End to end without a broker: the engine and the contract mapper share one seeded generator and
   * one clock, as in the application, so even message ids and timestamps repeat.
   */
  @Test
  void sameSeedAndClock_produceTheSameContractMessages() {
    List<String> first = runToJson(42);
    List<String> second = runToJson(42);

    assertThat(first).hasSize(60).doesNotHaveDuplicates().isEqualTo(second);
    assertThat(runToJson(43)).isNotEqualTo(first);
  }

  private static List<VehicleTelemetry> run(ScenarioConfig scenario, int ticks) {
    List<VehicleTelemetry> out = new ArrayList<>();
    SimulationEngine engine =
        new SimulationEngine(
            scenario,
            t -> {
              out.add(t);
              return CompletableFuture.completedFuture(null);
            },
            new Random(scenario.simulation().seed()));
    tick(engine, ticks);
    return out;
  }

  private static List<String> runToJson(long seed) {
    Random random = new Random(seed);
    Clock clock = Clock.fixed(Instant.parse("2026-11-16T10:00:00Z"), ZoneOffset.UTC);
    TelemetryMessageMapper mapper =
        new TelemetryMessageMapper(JsonMapper.builder().build(), clock, new RandomUuids(random));
    List<String> out = new ArrayList<>();
    SimulationEngine engine =
        new SimulationEngine(
            scenario(seed, 0.2),
            t -> {
              out.add(mapper.toJson(t));
              return CompletableFuture.completedFuture(null);
            },
            random);
    tick(engine, 20);
    return out;
  }
}
