package pt.ua.deti.tqs.p11.simulator.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import pt.ua.deti.tqs.p11.simulator.config.HubConfig;
import pt.ua.deti.tqs.p11.simulator.config.ScenarioConfig;
import pt.ua.deti.tqs.p11.simulator.config.SimulationParams;
import pt.ua.deti.tqs.p11.simulator.config.VehicleConfig;
import pt.ua.deti.tqs.p11.simulator.config.VehicleType;
import pt.ua.deti.tqs.p11.simulator.contract.RandomUuids;
import pt.ua.deti.tqs.p11.simulator.contract.TelemetryMessageMapper;
import pt.ua.deti.tqs.p11.simulator.event.FaultCatalog;
import pt.ua.deti.tqs.p11.simulator.telemetry.LockState;
import pt.ua.deti.tqs.p11.simulator.telemetry.Position;
import pt.ua.deti.tqs.p11.simulator.telemetry.VehicleTelemetry;
import tools.jackson.databind.json.JsonMapper;

/** No broker and no real time: the publisher is a list and the engine is driven by tick(). */
class SimulationEngineTest {

  private static final Position HUB_01 = new Position(40.6443, -8.6455);
  private static final Position HUB_02 = new Position(40.6412, -8.6501);
  private static final Position HUB_03 = new Position(40.6304, -8.6577);
  private static final Position HUB_04 = new Position(40.6421, -8.6524);

  private static final double TRIP_INTERVAL_SECONDS = 10;
  private static final double RIDE_SPEED_KMH = 15;

  /** 6 % per minute is exactly 1 % per ten-second reading. */
  private static final double TRIP_DRAIN_PERCENT_PER_MINUTE = 6;

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

  /**
   * Same hubs and vehicles as {@link #scenario}, with battery rates. One tick is one minute, so a
   * rate in percent per minute is exactly the change per tick.
   */
  private static ScenarioConfig scenarioWithRates(
      long seed, double faultProbability, double drain, double charge) {
    ScenarioConfig base = scenario(seed, faultProbability);
    return new ScenarioConfig(
        base.fleetId(),
        new SimulationParams(60, seed, faultProbability, drain, charge),
        base.hubs(),
        base.vehicles());
  }

  private List<Double> batteryReadings(String vehicleId) {
    return published.stream()
        .filter(t -> t.vehicleId().equals(vehicleId))
        .map(VehicleTelemetry::batteryPercent)
        .toList();
  }

  private static List<Optional<String>> faults(List<VehicleTelemetry> readings) {
    return readings.stream().map(VehicleTelemetry::faultCode).toList();
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
        .allSatisfy(t -> assertThat(t.faultCode()).isPresent().get().isIn(FaultCatalog.CODES));
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

  // ---- Battery: parked vehicles charge, never beyond 100 % ----

  @Test
  void firstReading_isTheInitialBatteryOfTheScenario_whateverTheRates() {
    engine(scenarioWithRates(1, 0, 0.5, 1.0)).tick();

    assertThat(published)
        .extracting(VehicleTelemetry::batteryPercent)
        .containsExactly(90.0, 20.0, 19.0);
  }

  @Test
  void parkedVehicles_chargeOneIntervalOfTheChargeRatePerTick() {
    tick(engine(scenarioWithRates(1, 0, 0.5, 1.0)), 3);

    assertThat(batteryReadings("scooter-0001")).containsExactly(20.0, 21.0, 22.0);
    assertThat(batteryReadings("bike-0002")).containsExactly(19.0, 20.0, 21.0);
  }

  @Test
  void charging_stopsAtOneHundredPercent() {
    tick(engine(scenarioWithRates(1, 0, 0.5, 1.0)), 13);

    List<Double> readings = batteryReadings("bike-0001");
    assertThat(readings).hasSize(13).allSatisfy(b -> assertThat(b).isLessThanOrEqualTo(100.0));
    assertThat(readings.get(9)).isEqualTo(99.0);
    assertThat(readings.subList(10, 13)).containsOnly(100.0);
  }

  @Test
  void parkedVehicles_withOnlyADrainRate_keepTheirBattery() {
    tick(engine(scenarioWithRates(1, 0, 5.0, 0)), 5);

    assertThat(batteryReadings("bike-0001")).hasSize(5).containsOnly(90.0);
    assertThat(batteryReadings("scooter-0001")).hasSize(5).containsOnly(20.0);
    assertThat(batteryReadings("bike-0002")).hasSize(5).containsOnly(19.0);
  }

  @Test
  void withoutRates_theBatteryNeverChanges() {
    tick(engine(scenario(1, 0)), 5);

    assertThat(batteryReadings("scooter-0001")).hasSize(5).containsOnly(20.0);
  }

  @Test
  void sameSeedAndScenarioWithRates_publishTheSameSequenceOfReadings() {
    List<VehicleTelemetry> first = run(scenarioWithRates(42, 0.2, 0.5, 1.0), 50);
    List<VehicleTelemetry> second = run(scenarioWithRates(42, 0.2, 0.5, 1.0), 50);

    assertThat(first).hasSize(150).usingRecursiveFieldByFieldElementComparator().isEqualTo(second);
  }

  /** The battery model draws no random numbers, so the rates never move the faults. */
  @Test
  void batteryRates_doNotChangeTheFaultSequence() {
    List<Optional<String>> withoutRates = faults(run(scenario(42, 0.2), 50));
    List<Optional<String>> withRates = faults(run(scenarioWithRates(42, 0.2, 0.5, 1.0), 50));

    assertThat(withRates).hasSize(150).isEqualTo(withoutRates);
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

  private static final List<HubConfig> TWO_HUBS =
      List.of(
          new HubConfig("hub-01", "Hub one", HUB_01), new HubConfig("hub-02", "Hub two", HUB_02));

  private static final List<HubConfig> FOUR_HUBS =
      List.of(
          new HubConfig("hub-01", "Hub one", HUB_01),
          new HubConfig("hub-02", "Hub two", HUB_02),
          new HubConfig("hub-03", "Hub three", HUB_03),
          new HubConfig("hub-04", "Hub four", HUB_04));

  private static final VehicleConfig BIKE_AT_HUB_01 =
      new VehicleConfig("bike-0001", VehicleType.E_BIKE, "hub-01", 90);

  private static final List<VehicleConfig> THREE_VEHICLES =
      List.of(
          BIKE_AT_HUB_01,
          new VehicleConfig("scooter-0001", VehicleType.E_SCOOTER, "hub-03", 80),
          new VehicleConfig("bike-0002", VehicleType.E_BIKE, "hub-04", 70));

  /** No charging, so a parked vehicle keeps its battery and only riding changes it. */
  private static ScenarioConfig tripScenario(
      double tripProbability,
      double faultProbability,
      List<HubConfig> hubs,
      List<VehicleConfig> vehicles) {
    return new ScenarioConfig(
        "fleet-01",
        new SimulationParams(
            TRIP_INTERVAL_SECONDS,
            7,
            faultProbability,
            TRIP_DRAIN_PERCENT_PER_MINUTE,
            0,
            tripProbability,
            RIDE_SPEED_KMH),
        hubs,
        vehicles);
  }

  private static List<VehicleTelemetry> alwaysRidingBike(int ticks) {
    return run(tripScenario(1, 0, TWO_HUBS, List.of(BIKE_AT_HUB_01)), ticks);
  }

  /**
   * The readings of the first trip: every unlocked reading, then the locked reading at the
   * destination.
   */
  private static List<VehicleTelemetry> firstTrip(List<VehicleTelemetry> readings) {
    int start = indexOfLockState(readings, LockState.UNLOCKED, 0);
    int end = indexOfLockState(readings, LockState.LOCKED, start);
    return readings.subList(start, end + 1);
  }

  private static int indexOfLockState(List<VehicleTelemetry> readings, LockState state, int from) {
    for (int i = from; i < readings.size(); i++) {
      if (readings.get(i).lockState() == state) {
        return i;
      }
    }
    throw new AssertionError("No " + state + " reading from index " + from);
  }

  private static double degreesBetween(Position a, Position b) {
    return Math.hypot(a.lat() - b.lat(), a.lon() - b.lon());
  }

  @Test
  void tripProbabilityZero_vehiclesNeverLeaveTheirHub() {
    List<VehicleTelemetry> readings =
        run(tripScenario(0, 0, TWO_HUBS, List.of(BIKE_AT_HUB_01)), 100);

    assertThat(readings)
        .hasSize(100)
        .allSatisfy(t -> {
          assertThat(t.lockState()).isEqualTo(LockState.LOCKED);
          assertThat(t.hubId()).contains("hub-01");
          assertThat(t.position()).contains(HUB_01);
        });
  }

  @Test
  void firstReading_isStillTheParkedStateOfTheScenario_evenWhenTripsAlwaysStart() {
    VehicleTelemetry first = alwaysRidingBike(1).get(0);

    assertThat(first.lockState()).isEqualTo(LockState.LOCKED);
    assertThat(first.hubId()).contains("hub-01");
  }

  @Test
  void leavingAHub_theVehicleIsUnlockedInNoHubAtTheRideSpeed() {
    VehicleTelemetry leaving = alwaysRidingBike(2).get(1);

    assertThat(leaving.lockState()).isEqualTo(LockState.UNLOCKED);
    assertThat(leaving.hubId()).isEmpty();
    assertThat(leaving.position()).contains(HUB_01);
    assertThat(leaving.speedKmh()).contains(RIDE_SPEED_KMH);
  }

  @Test
  void duringATrip_thePositionMovesGraduallyTowardsTheDestinationHub() {
    List<VehicleTelemetry> trip = firstTrip(alwaysRidingBike(40));
    List<VehicleTelemetry> riding = trip.subList(0, trip.size() - 1);

    assertThat(riding)
        .hasSizeGreaterThan(3)
        .allSatisfy(t -> {
          assertThat(t.lockState()).isEqualTo(LockState.UNLOCKED);
          assertThat(t.hubId()).isEmpty();
        });
    List<Double> distancesToDestination =
        riding.stream().map(t -> degreesBetween(t.position().orElseThrow(), HUB_02)).toList();
    assertThat(distancesToDestination)
        .isSortedAccordingTo(Comparator.reverseOrder())
        .doesNotHaveDuplicates();
  }

  @Test
  void aTrip_lastsTheDistanceBetweenTheHubsAtTheRideSpeed() {
    double durationSeconds = Trip.between(HUB_01, HUB_02, RIDE_SPEED_KMH).durationSeconds();

    List<VehicleTelemetry> trip = firstTrip(alwaysRidingBike(40));

    // One unlocked reading per started interval of the trip, then the arrival.
    assertThat(trip).hasSize((int) Math.ceil(durationSeconds / TRIP_INTERVAL_SECONDS) + 1);
  }

  @Test
  void whenATripEnds_theVehicleIsLockedAtTheDestinationHubWithItsExactPosition() {
    VehicleTelemetry arrival = firstTrip(alwaysRidingBike(40)).getLast();

    assertThat(arrival.lockState()).isEqualTo(LockState.LOCKED);
    assertThat(arrival.hubId()).contains("hub-02");
    assertThat(arrival.position()).contains(HUB_02);
    assertThat(arrival.speedKmh()).contains(0.0);
  }

  @Test
  void ridingVehicle_drainsOneIntervalOfTheDrainRatePerReading() {
    List<VehicleTelemetry> trip = firstTrip(alwaysRidingBike(40));

    // Parked without charging until it leaves, then 1 % less per reading.
    for (int i = 0; i < trip.size(); i++) {
      assertThat(trip.get(i).batteryPercent()).isCloseTo(90.0 - i, within(1e-9));
    }
  }

  @Test
  void withSeveralHubs_positionsStayInsideTheAreaOfTheHubs() {
    List<VehicleTelemetry> readings = run(tripScenario(0.3, 0, FOUR_HUBS, THREE_VEHICLES), 500);

    double minLat = FOUR_HUBS.stream().mapToDouble(h -> h.position().lat()).min().orElseThrow();
    double maxLat = FOUR_HUBS.stream().mapToDouble(h -> h.position().lat()).max().orElseThrow();
    double minLon = FOUR_HUBS.stream().mapToDouble(h -> h.position().lon()).min().orElseThrow();
    double maxLon = FOUR_HUBS.stream().mapToDouble(h -> h.position().lon()).max().orElseThrow();
    assertThat(readings).anySatisfy(t -> assertThat(t.lockState()).isEqualTo(LockState.UNLOCKED));
    assertThat(readings)
        .allSatisfy(t -> {
          Position p = t.position().orElseThrow();
          assertThat(p.lat()).isBetween(minLat, maxLat);
          assertThat(p.lon()).isBetween(minLon, maxLon);
        });
  }

  @Test
  void withSeveralHubs_aParkedVehicleIsAlwaysAtThePositionOfItsHub() {
    Map<String, Position> hubPositions =
        FOUR_HUBS.stream().collect(Collectors.toMap(HubConfig::id, HubConfig::position));

    List<VehicleTelemetry> readings = run(tripScenario(0.3, 0, FOUR_HUBS, THREE_VEHICLES), 500);

    List<VehicleTelemetry> parked = readings.stream().filter(t -> t.hubId().isPresent()).toList();
    assertThat(parked)
        .allSatisfy(t -> assertThat(t.position()).contains(hubPositions.get(t.hubId().get())));
    // Every hub is reached at least once, so trips really end at hubs other than the start.
    assertThat(parked)
        .extracting(t -> t.hubId().get())
        .contains("hub-01", "hub-02", "hub-03", "hub-04");
  }

  @Test
  void faultyVehicle_neverStartsATrip() {
    assertThat(run(tripScenario(1, 1, TWO_HUBS, List.of(BIKE_AT_HUB_01)), 50))
        .allSatisfy(t -> assertThat(t.lockState()).isEqualTo(LockState.LOCKED));
  }

  @Test
  void withASingleHub_theVehicleHasNowhereToGoAndStaysParked() {
    List<HubConfig> oneHub = List.of(TWO_HUBS.get(0));

    assertThat(run(tripScenario(1, 0, oneHub, List.of(BIKE_AT_HUB_01)), 50))
        .allSatisfy(t -> assertThat(t.lockState()).isEqualTo(LockState.LOCKED));
  }

  @Test
  void aHubAtTheSamePlace_isNotADestination() {
    List<HubConfig> samePlace =
        List.of(
            new HubConfig("hub-01", "Hub one", HUB_01), new HubConfig("hub-02", "Twin", HUB_01));

    assertThat(run(tripScenario(1, 0, samePlace, List.of(BIKE_AT_HUB_01)), 50))
        .allSatisfy(t -> assertThat(t.lockState()).isEqualTo(LockState.LOCKED));
  }

  @Test
  void sameSeedAndScenarioWithTrips_publishTheSameSequenceOfReadings() {
    List<VehicleTelemetry> first = run(tripScenario(0.3, 0.05, FOUR_HUBS, THREE_VEHICLES), 200);
    List<VehicleTelemetry> second = run(tripScenario(0.3, 0.05, FOUR_HUBS, THREE_VEHICLES), 200);

    assertThat(first).hasSize(600).usingRecursiveFieldByFieldElementComparator().isEqualTo(second);
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

  private static final String BIKE = "bike-0001";
  private static final CommandOutcome COMPLETED = CommandOutcome.completed();

  /** One bike at hub-01 that only the platform moves (no automatic trips, no faults). */
  private SimulationEngine commandedBike(List<HubConfig> hubs) {
    return engine(tripScenario(0, 0, hubs, List.of(BIKE_AT_HUB_01)));
  }

  private VehicleTelemetry lastReading() {
    return published.getLast();
  }

  /**
   * Readings of the commanded bike between four hubs: one tick, {@code commands}, then {@code
   * ticks} more. Two runs that differ only in commands that should change nothing must be equal.
   */
  private static List<VehicleTelemetry> commandedRun(
      Consumer<SimulationEngine> commands, int ticks) {
    List<VehicleTelemetry> out = new ArrayList<>();
    ScenarioConfig scenario = tripScenario(0, 0, FOUR_HUBS, List.of(BIKE_AT_HUB_01));
    SimulationEngine engine =
        new SimulationEngine(
            scenario,
            t -> {
              out.add(t);
              return CompletableFuture.completedFuture(null);
            },
            new Random(scenario.simulation().seed()));
    engine.tick();
    commands.accept(engine);
    tick(engine, ticks);
    return out;
  }

  @Test
  void unlock_parkedVehicle_isCompleted_andTheNextReadingShowsItUnlockedLeavingTheHub() {
    SimulationEngine engine = commandedBike(TWO_HUBS);
    engine.tick();

    assertThat(engine.unlock(BIKE)).isEqualTo(COMPLETED);
    engine.tick();

    VehicleTelemetry leaving = lastReading();
    assertThat(leaving.lockState()).isEqualTo(LockState.UNLOCKED);
    assertThat(leaving.hubId()).isEmpty();
    assertThat(leaving.position()).contains(HUB_01);
    assertThat(leaving.speedKmh()).contains(RIDE_SPEED_KMH);
  }

  @Test
  void unlock_vehicleAlreadyWaitingAtAHub_isCompleted_andItKeepsWaitingThere() {
    SimulationEngine engine = commandedBike(TWO_HUBS);
    engine.tick();
    engine.unlock(BIKE);
    tick(engine, 40);

    // Starting a new trip here would take the bike away from hub-02.
    assertThat(engine.unlock(BIKE)).isEqualTo(COMPLETED);
    tick(engine, 2);

    assertThat(lastReading().lockState()).isEqualTo(LockState.UNLOCKED);
    assertThat(lastReading().hubId()).contains("hub-02");
    assertThat(lastReading().speedKmh()).contains(0.0);
  }

  @Test
  void unlockedVehicle_ridesGraduallyTowardsAnotherHub() {
    SimulationEngine engine = commandedBike(TWO_HUBS);
    engine.tick();
    engine.unlock(BIKE);

    tick(engine, 12);

    List<Double> distancesToHub02 =
        published.subList(1, published.size()).stream()
            .map(t -> degreesBetween(t.position().orElseThrow(), HUB_02))
            .toList();
    assertThat(distancesToHub02)
        .hasSize(12)
        .isSortedAccordingTo(Comparator.reverseOrder())
        .doesNotHaveDuplicates();
  }

  @Test
  void platformTrip_onArrival_waitsUnlockedAtTheDestinationHubUntilLocked() {
    SimulationEngine engine = commandedBike(TWO_HUBS);
    engine.tick();
    engine.unlock(BIKE);

    tick(engine, 40);

    // The trip takes 13 readings; the last 10 of 41 are all after the arrival.
    assertThat(published.subList(31, 41))
        .allSatisfy(t -> {
          assertThat(t.lockState()).isEqualTo(LockState.UNLOCKED);
          assertThat(t.hubId()).contains("hub-02");
          assertThat(t.position()).contains(HUB_02);
          assertThat(t.speedKmh()).contains(0.0);
        });
  }

  @Test
  void waitingUnlockedAtAHub_theBatteryDoesNotChange() {
    SimulationEngine engine = commandedBike(TWO_HUBS);
    engine.tick();
    engine.unlock(BIKE);

    tick(engine, 40);

    // Riding drained 1 % per reading; waiting neither drains nor charges.
    assertThat(published.subList(31, 41))
        .extracting(VehicleTelemetry::batteryPercent)
        .containsOnly(published.get(31).batteryPercent());
    assertThat(published.get(31).batteryPercent()).isLessThan(90.0);
  }

  @Test
  void unlock_vehicleAlreadyUnlocked_isCompleted_andChangesNothing() {
    // The second unlock comes in the middle of the trip: restarting the trip would be caught.
    List<VehicleTelemetry> once =
        commandedRun(
            engine -> {
              engine.unlock(BIKE);
              tick(engine, 3);
            },
            10);
    List<VehicleTelemetry> twice =
        commandedRun(
            engine -> {
              engine.unlock(BIKE);
              tick(engine, 3);
              assertThat(engine.unlock(BIKE)).isEqualTo(COMPLETED);
            },
            10);

    assertThat(twice).hasSize(14).usingRecursiveFieldByFieldElementComparator().isEqualTo(once);
  }

  @Test
  void unlock_faultyVehicle_isRejectedWithVehicleFault_andItStaysLocked() {
    SimulationEngine engine = engine(tripScenario(0, 1, TWO_HUBS, List.of(BIKE_AT_HUB_01)));
    engine.tick();

    assertThat(engine.unlock(BIKE)).isEqualTo(CommandOutcome.rejected("VEHICLE_FAULT"));
    engine.tick();

    assertThat(lastReading().lockState()).isEqualTo(LockState.LOCKED);
    assertThat(lastReading().hubId()).contains("hub-01");
  }

  @Test
  void unlock_withNoOtherHub_isCompleted_andTheVehicleWaitsUnlockedAtItsHub() {
    SimulationEngine engine = commandedBike(List.of(TWO_HUBS.get(0)));
    engine.tick();

    assertThat(engine.unlock(BIKE)).isEqualTo(COMPLETED);
    tick(engine, 3);

    assertThat(lastReading().lockState()).isEqualTo(LockState.UNLOCKED);
    assertThat(lastReading().hubId()).contains("hub-01");
    assertThat(lastReading().position()).contains(HUB_01);
  }

  @Test
  void unlock_vehicleOnAnAutomaticTrip_isCompleted_andItWaitsForALockOnArrival() {
    SimulationEngine engine = engine(tripScenario(1, 0, TWO_HUBS, List.of(BIKE_AT_HUB_01)));
    tick(engine, 3);
    assertThat(lastReading().lockState()).isEqualTo(LockState.UNLOCKED);

    assertThat(engine.unlock(BIKE)).isEqualTo(COMPLETED);
    tick(engine, 40);

    // An automatic trip would lock at hub-02; handed over to the platform, it waits unlocked.
    assertThat(published.subList(published.size() - 10, published.size()))
        .allSatisfy(t -> {
          assertThat(t.lockState()).isEqualTo(LockState.UNLOCKED);
          assertThat(t.hubId()).contains("hub-02");
        });
  }

  @Test
  void lock_vehicleWaitingAtAHub_isCompleted_andLocksItAtThatHub() {
    SimulationEngine engine = commandedBike(TWO_HUBS);
    engine.tick();
    engine.unlock(BIKE);
    tick(engine, 40);

    assertThat(engine.lock(BIKE)).isEqualTo(COMPLETED);
    engine.tick();

    VehicleTelemetry locked = lastReading();
    assertThat(locked.lockState()).isEqualTo(LockState.LOCKED);
    assertThat(locked.hubId()).contains("hub-02");
    assertThat(locked.position()).contains(HUB_02);
    assertThat(locked.speedKmh()).contains(0.0);
  }

  @Test
  void lock_beforeTheVehicleMoved_isCompleted_andLocksItAtTheHubItWasLeaving() {
    SimulationEngine engine = commandedBike(TWO_HUBS);
    engine.tick();
    engine.unlock(BIKE);

    assertThat(engine.lock(BIKE)).isEqualTo(COMPLETED);
    engine.tick();

    assertThat(lastReading().lockState()).isEqualTo(LockState.LOCKED);
    assertThat(lastReading().hubId()).contains("hub-01");
    assertThat(lastReading().position()).contains(HUB_01);
  }

  @Test
  void lock_betweenHubs_isRejectedWithNotAtHub_andTheTripGoesOn() {
    SimulationEngine engine = commandedBike(TWO_HUBS);
    engine.tick();
    engine.unlock(BIKE);
    // One interval of riding: the bike has just left hub-01.
    engine.tick();

    assertThat(engine.lock(BIKE)).isEqualTo(CommandOutcome.rejected("NOT_AT_HUB"));
    engine.tick();

    assertThat(lastReading().lockState()).isEqualTo(LockState.UNLOCKED);
    assertThat(lastReading().hubId()).isEmpty();
    assertThat(lastReading().position()).isNotEqualTo(Optional.of(HUB_01));
  }

  @Test
  void lock_vehicleAlreadyLocked_isCompleted_andChangesNothing() {
    List<VehicleTelemetry> withoutLock = commandedRun(engine -> {}, 4);
    List<VehicleTelemetry> withLock =
        commandedRun(engine -> assertThat(engine.lock(BIKE)).isEqualTo(COMPLETED), 4);

    assertThat(withLock)
        .hasSize(5)
        .usingRecursiveFieldByFieldElementComparator()
        .isEqualTo(withoutLock);
  }

  @Test
  void ping_knownVehicle_isCompleted_andChangesNothing() {
    List<VehicleTelemetry> withoutPing = commandedRun(engine -> {}, 4);
    List<VehicleTelemetry> withPing =
        commandedRun(engine -> assertThat(engine.ping(BIKE)).isEqualTo(COMPLETED), 4);

    assertThat(withPing)
        .hasSize(5)
        .usingRecursiveFieldByFieldElementComparator()
        .isEqualTo(withoutPing);
  }

  @Test
  void everyCommand_toAnUnknownVehicle_isRejectedWithUnknownVehicle() {
    SimulationEngine engine = commandedBike(TWO_HUBS);
    CommandOutcome unknown = CommandOutcome.rejected("UNKNOWN_VEHICLE");

    assertThat(engine.unlock("bike-9999")).isEqualTo(unknown);
    assertThat(engine.lock("bike-9999")).isEqualTo(unknown);
    assertThat(engine.ping("bike-9999")).isEqualTo(unknown);
  }
}
