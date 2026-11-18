package pt.ua.deti.tqs.p11.simulator.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import pt.ua.deti.tqs.p11.simulator.config.HubConfig;
import pt.ua.deti.tqs.p11.simulator.config.ScenarioConfig;
import pt.ua.deti.tqs.p11.simulator.config.ScriptedFault;
import pt.ua.deti.tqs.p11.simulator.config.SimulationParams;
import pt.ua.deti.tqs.p11.simulator.config.VehicleConfig;
import pt.ua.deti.tqs.p11.simulator.config.VehicleType;
import pt.ua.deti.tqs.p11.simulator.event.FaultCatalog;
import pt.ua.deti.tqs.p11.simulator.event.FaultEvent;
import pt.ua.deti.tqs.p11.simulator.event.FaultEventType;
import pt.ua.deti.tqs.p11.simulator.event.Severity;
import pt.ua.deti.tqs.p11.simulator.telemetry.Position;
import pt.ua.deti.tqs.p11.simulator.telemetry.VehicleTelemetry;

/** Faults reported as events. No broker and no real time: both publishers are lists. */
class SimulationEngineFaultEventsTest {

  private final List<VehicleTelemetry> readings = new ArrayList<>();
  private final List<FaultEvent> events = new ArrayList<>();

  private static ScenarioConfig scenario(long seed, double faultProbability) {
    return new ScenarioConfig(
        "fleet-01",
        new SimulationParams(10, seed, faultProbability),
        List.of(new HubConfig("hub-01", "Hub one", new Position(40.6443, -8.6455))),
        List.of(
            new VehicleConfig("bike-0001", VehicleType.E_BIKE, "hub-01", 90),
            new VehicleConfig("bike-0002", VehicleType.E_BIKE, "hub-01", 80),
            new VehicleConfig("bike-0003", VehicleType.E_BIKE, "hub-01", 70)));
  }

  private SimulationEngine engine(ScenarioConfig scenario) {
    return new SimulationEngine(
        scenario,
        telemetry -> {
          readings.add(telemetry);
          return CompletableFuture.completedFuture(null);
        },
        event -> {
          events.add(event);
          return CompletableFuture.completedFuture(null);
        },
        new Random(scenario.simulation().seed()));
  }

  private static void tick(SimulationEngine engine, int times) {
    for (int i = 0; i < times; i++) {
      engine.tick();
    }
  }

  @Test
  void faultProbabilityZero_reportsNoEvent() {
    tick(engine(scenario(7, 0)), 100);

    assertThat(events).isEmpty();
  }

  @Test
  void faultProbabilityOne_raisesOneFaultRaisedEventPerVehicle_inScenarioOrder() {
    tick(engine(scenario(7, 1)), 1);

    assertThat(events)
        .extracting(FaultEvent::vehicleId)
        .containsExactly("bike-0001", "bike-0002", "bike-0003");
    assertThat(events)
        .allSatisfy(event -> {
          assertThat(event.fleetId()).isEqualTo("fleet-01");
          assertThat(event.type()).isEqualTo(FaultEventType.FAULT_RAISED);
        });
  }

  @Test
  void aFaultIsReportedOnlyOnce_notAgainOnEveryTickWhileItStays() {
    tick(engine(scenario(7, 1)), 30);

    assertThat(events).hasSize(3);
  }

  @Test
  void eventCarriesTheSameFaultCodeAsTheTelemetryOfTheVehicle() {
    tick(engine(scenario(7, 1)), 1);

    Map<String, String> telemetryCodes =
        readings.stream()
            .collect(
                Collectors.toMap(VehicleTelemetry::vehicleId, t -> t.faultCode().orElseThrow()));
    assertThat(events)
        .hasSize(3)
        .allSatisfy(event ->
            assertThat(event.faultCode()).isEqualTo(telemetryCodes.get(event.vehicleId())));
  }

  @Test
  void severity_isCriticalForOverheatAndWarningForTheOtherFaults() {
    Set<String> seenCodes = new java.util.HashSet<>();
    for (long seed = 1; seed <= 30; seed++) {
      events.clear();
      tick(engine(scenario(seed, 1)), 1);
      for (FaultEvent event : events) {
        seenCodes.add(event.faultCode());
        Severity expected =
            event.faultCode().equals("BATTERY_OVERHEAT") ? Severity.CRITICAL : Severity.WARNING;
        assertThat(event.severity()).as(event.faultCode()).isEqualTo(expected);
      }
    }
    // The loop must have met every code, or the check above would prove nothing about some of them.
    assertThat(seenCodes).containsExactlyInAnyOrderElementsOf(FaultCatalog.CODES);
  }

  @Test
  void sameSeedAndScenario_reportTheSameEvents() {
    tick(engine(scenario(42, 0.2)), 60);
    List<FaultEvent> first = List.copyOf(events);
    events.clear();
    readings.clear();

    tick(engine(scenario(42, 0.2)), 60);

    assertThat(events).isNotEmpty().isEqualTo(first);
  }

  // ---- Scripted faults: raised and cleared at set moments, with no chance involved. ----

  /** Same fleet as {@link #scenario}, with no random faults and the given scripted ones. */
  private static ScenarioConfig scriptedScenario(ScriptedFault... faults) {
    ScenarioConfig base = scenario(1, 0);
    return new ScenarioConfig(
        base.fleetId(), base.simulation(), base.hubs(), base.vehicles(), List.of(faults));
  }

  private static ScriptedFault fault(
      String vehicleId, String code, double atSeconds, Double clearAfterSeconds) {
    return new ScriptedFault(
        vehicleId,
        code,
        atSeconds,
        clearAfterSeconds == null ? OptionalDouble.empty() : OptionalDouble.of(clearAfterSeconds));
  }

  /** What one vehicle reported at each tick, in order: its fault code, or empty if healthy. */
  private List<Optional<String>> faultsReportedBy(String vehicleId) {
    return readings.stream()
        .filter(t -> t.vehicleId().equals(vehicleId))
        .map(VehicleTelemetry::faultCode)
        .toList();
  }

  @Test
  void scriptedFaultAtZero_isRaisedOnTheFirstTick_andReportedInThatReading() {
    tick(engine(scriptedScenario(fault("bike-0002", "FLAT_TIRE", 0, null))), 1);

    assertThat(events)
        .containsExactly(
            new FaultEvent(
                "fleet-01",
                "bike-0002",
                FaultEventType.FAULT_RAISED,
                "FLAT_TIRE",
                Severity.WARNING));
    assertThat(faultsReportedBy("bike-0002")).containsExactly(Optional.of("FLAT_TIRE"));
  }

  @Test
  void scriptedFault_isRaisedOnTheFirstTickAtOrAfterItsTime() {
    // Ticks are 10 s apart: the fourth tick is at 30 s. 30 is on a tick; 21 falls between two.
    for (double atSeconds : List.of(30.0, 29.9, 21.0)) {
      readings.clear();
      events.clear();
      SimulationEngine engine =
          engine(scriptedScenario(fault("bike-0001", "BRAKE_WEAR", atSeconds, null)));

      tick(engine, 3);
      assertThat(events).as("before %s s", atSeconds).isEmpty();
      assertThat(faultsReportedBy("bike-0001"))
          .as("before %s s", atSeconds)
          .containsOnly(Optional.empty());

      tick(engine, 1);
      assertThat(events).as("at %s s", atSeconds).hasSize(1);
      assertThat(faultsReportedBy("bike-0001").getLast()).contains("BRAKE_WEAR");
    }
  }

  @Test
  void scriptedFault_justAfterATick_waitsForTheNextTick() {
    // 30.1 s is after the fourth tick (30 s), so the fault appears at the fifth (40 s).
    SimulationEngine engine =
        engine(scriptedScenario(fault("bike-0001", "BRAKE_WEAR", 30.1, null)));

    tick(engine, 4);
    assertThat(events).isEmpty();

    tick(engine, 1);
    assertThat(events).hasSize(1);
  }

  @Test
  void scriptedFault_affectsOnlyItsVehicle() {
    tick(engine(scriptedScenario(fault("bike-0002", "BRAKE_WEAR", 0, null))), 5);

    assertThat(faultsReportedBy("bike-0001")).containsOnly(Optional.empty());
    assertThat(faultsReportedBy("bike-0003")).containsOnly(Optional.empty());
    assertThat(events).extracting(FaultEvent::vehicleId).containsOnly("bike-0002");
  }

  @Test
  void scriptedOverheat_isReportedAsCritical() {
    tick(engine(scriptedScenario(fault("bike-0001", "BATTERY_OVERHEAT", 0, null))), 1);

    assertThat(events).extracting(FaultEvent::severity).containsExactly(Severity.CRITICAL);
  }

  @Test
  void faultWithoutClearTime_staysAndIsNeverClearedEvenAfterManyTicks() {
    tick(engine(scriptedScenario(fault("bike-0001", "FLAT_TIRE", 0, null))), 200);

    assertThat(faultsReportedBy("bike-0001")).containsOnly(Optional.of("FLAT_TIRE"));
    assertThat(events).extracting(FaultEvent::type).containsExactly(FaultEventType.FAULT_RAISED);
  }

  @Test
  void faultWithClearTime_keepsReportingUntilItIsCleared_thenReportsHealthy() {
    // Raised at the second tick (10 s), cleared 25 s later: at the first tick from 35 s, the
    // fifth (40 s). The vehicle reports the fault on ticks two, three and four.
    tick(engine(scriptedScenario(fault("bike-0001", "BRAKE_WEAR", 10, 25.0))), 7);

    assertThat(faultsReportedBy("bike-0001"))
        .containsExactly(
            Optional.empty(),
            Optional.of("BRAKE_WEAR"),
            Optional.of("BRAKE_WEAR"),
            Optional.of("BRAKE_WEAR"),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
    assertThat(events)
        .containsExactly(
            new FaultEvent(
                "fleet-01",
                "bike-0001",
                FaultEventType.FAULT_RAISED,
                "BRAKE_WEAR",
                Severity.WARNING),
            new FaultEvent(
                "fleet-01",
                "bike-0001",
                FaultEventType.FAULT_CLEARED,
                "BRAKE_WEAR",
                Severity.WARNING));
  }

  @Test
  void clearTime_countsFromTheMomentTheFaultWasRaised_notFromTheStartOfTheRun() {
    // Raised at 30 s, cleared after 10 s: reported on the fourth tick only, healthy on the fifth.
    tick(engine(scriptedScenario(fault("bike-0001", "FLAT_TIRE", 30, 10.0))), 6);

    assertThat(faultsReportedBy("bike-0001"))
        .containsExactly(
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.of("FLAT_TIRE"),
            Optional.empty(),
            Optional.empty());
  }

  @Test
  void secondScriptedFault_waitsUntilTheFirstIsCleared() {
    // bike-0001 has BRAKE_WEAR from 0 to 20 s. FLAT_TIRE is due at 10 s but the vehicle is not
    // healthy until the third tick (20 s), where the first is cleared and the second raised.
    SimulationEngine engine =
        engine(
            scriptedScenario(
                fault("bike-0001", "BRAKE_WEAR", 0, 20.0),
                fault("bike-0001", "FLAT_TIRE", 10, null)));

    tick(engine, 4);

    assertThat(faultsReportedBy("bike-0001"))
        .containsExactly(
            Optional.of("BRAKE_WEAR"),
            Optional.of("BRAKE_WEAR"),
            Optional.of("FLAT_TIRE"),
            Optional.of("FLAT_TIRE"));
    assertThat(events)
        .extracting(e -> e.type() + " " + e.faultCode())
        .containsExactly(
            "FAULT_RAISED BRAKE_WEAR", "FAULT_CLEARED BRAKE_WEAR", "FAULT_RAISED FLAT_TIRE");
  }

  @Test
  void vehicleWithAScriptedFault_cannotBeUnlockedUntilTheFaultIsCleared() {
    SimulationEngine engine = engine(scriptedScenario(fault("bike-0001", "BRAKE_WEAR", 0, 15.0)));

    tick(engine, 1);
    assertThat(engine.unlock("bike-0001")).isEqualTo(CommandOutcome.rejected("VEHICLE_FAULT"));

    tick(engine, 2);
    assertThat(faultsReportedBy("bike-0001").getLast()).isEmpty();
    assertThat(engine.unlock("bike-0001")).isEqualTo(CommandOutcome.completed());
  }

  @Test
  void scriptedFaultForAnUnknownVehicle_isRejectedWhenTheEngineIsBuilt() {
    ScenarioConfig scenario = scriptedScenario(fault("bike-9999", "BRAKE_WEAR", 0, null));

    assertThatThrownBy(() -> engine(scenario))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("bike-9999");
  }
}
