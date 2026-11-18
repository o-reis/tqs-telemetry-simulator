package pt.ua.deti.tqs.p11.simulator.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

/** The optional {@code faults} section of a scenario file. */
class ScenarioParserFaultsTest {

  private final ScenarioParser parser = new ScenarioParser();

  private static Map<String, Object> obj(Object... keysAndValues) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      if (keysAndValues[i + 1] != null) {
        map.put((String) keysAndValues[i], keysAndValues[i + 1]);
      }
    }
    return map;
  }

  private static Map<String, Object> fault(
      Object vehicle, Object code, Object atSeconds, Object clearAfterSeconds) {
    return obj(
        "vehicle",
        vehicle,
        "code",
        code,
        "atSeconds",
        atSeconds,
        "clearAfterSeconds",
        clearAfterSeconds);
  }

  /** A valid scenario with one vehicle, {@code bike-0001}, plus the given faults section. */
  private static Map<String, Object> scenarioWithFaults(Object faults) {
    return obj(
        "fleetId", "fleet-01",
        "simulation", obj("emissionIntervalSeconds", 10, "seed", 42, "faultProbability", 0),
        "hubs",
            List.of(
                obj(
                    "id", "hub-01",
                    "name", "Hub one",
                    "position", obj("lat", 40.6443, "lon", -8.6455))),
        "vehicles",
            List.of(obj("id", "bike-0001", "type", "E_BIKE", "hub", "hub-01", "battery", 85)),
        "faults", faults);
  }

  private void assertRejectedWith(Map<String, Object> raw, String... problems) {
    assertThatThrownBy(() -> parser.parse(raw))
        .isInstanceOfSatisfying(
            InvalidScenarioException.class,
            e -> assertThat(e.getProblems()).containsExactly(problems));
  }

  @Test
  void parse_scenarioWithoutFaults_hasNoScriptedFaults() {
    assertThat(parser.parse(scenarioWithFaults(null)).faults()).isEmpty();
  }

  @Test
  void parse_emptyFaultsList_hasNoScriptedFaults() {
    assertThat(parser.parse(scenarioWithFaults(List.of())).faults()).isEmpty();
  }

  @Test
  void parse_fault_keepsVehicleCodeAndTimes() {
    ScenarioConfig config =
        parser.parse(scenarioWithFaults(List.of(fault("bike-0001", "BRAKE_WEAR", 30, 60))));

    assertThat(config.faults())
        .containsExactly(new ScriptedFault("bike-0001", "BRAKE_WEAR", 30, OptionalDouble.of(60)));
  }

  @Test
  void parse_faultWithoutClearAfterSeconds_staysForTheRestOfTheRun() {
    ScenarioConfig config =
        parser.parse(scenarioWithFaults(List.of(fault("bike-0001", "FLAT_TIRE", 0, null))));

    assertThat(config.faults())
        .containsExactly(new ScriptedFault("bike-0001", "FLAT_TIRE", 0, OptionalDouble.empty()));
  }

  @Test
  void parse_severalFaults_keepTheOrderOfTheFile() {
    ScenarioConfig config =
        parser.parse(
            scenarioWithFaults(
                List.of(
                    fault("bike-0001", "FLAT_TIRE", 50, null),
                    fault("bike-0001", "BRAKE_WEAR", 10, 5))));

    assertThat(config.faults())
        .extracting(ScriptedFault::faultCode)
        .containsExactly("FLAT_TIRE", "BRAKE_WEAR");
  }

  @Test
  void parse_faultOfAnUnknownVehicle_isRejectedListingTheKnownVehicles() {
    assertRejectedWith(
        scenarioWithFaults(List.of(fault("bike-9999", "BRAKE_WEAR", 0, null))),
        "faults[0].vehicle: unknown vehicle 'bike-9999' (known: bike-0001)");
  }

  @Test
  void parse_faultWithAnUnknownCode_isRejectedListingTheKnownCodes() {
    assertRejectedWith(
        scenarioWithFaults(List.of(fault("bike-0001", "MOTOR_STALL", 0, null))),
        "faults[0].code: unknown fault code 'MOTOR_STALL'"
            + " (known: BRAKE_WEAR, FLAT_TIRE, BATTERY_OVERHEAT)");
  }

  @Test
  void parse_faultWithNegativeStart_isRejected() {
    assertRejectedWith(
        scenarioWithFaults(List.of(fault("bike-0001", "BRAKE_WEAR", -1, null))),
        "faults[0].atSeconds: must be zero or more and finite (was -1.0)");
  }

  @Test
  void parse_faultStartingAtZero_isAccepted() {
    assertThat(
            parser
                .parse(scenarioWithFaults(List.of(fault("bike-0001", "BRAKE_WEAR", 0, null))))
                .faults())
        .hasSize(1);
  }

  @Test
  void parse_faultClearedAfterZeroSeconds_isRejected() {
    assertRejectedWith(
        scenarioWithFaults(List.of(fault("bike-0001", "BRAKE_WEAR", 5, 0))),
        "faults[0].clearAfterSeconds: must be greater than 0 and finite (was 0.0)");
  }

  @Test
  void parse_faultMissingRequiredFields_reportsEachOne() {
    assertRejectedWith(
        scenarioWithFaults(List.of(obj("clearAfterSeconds", 5))),
        "faults[0].vehicle: is required",
        "faults[0].code: is required",
        "faults[0].atSeconds: is required");
  }

  @Test
  void parse_faultsThatIsNotAList_isRejected() {
    assertRejectedWith(scenarioWithFaults("soon"), "faults: must be a list (was 'soon')");
  }

  @Test
  void parse_faultThatIsNotAnObject_isRejected() {
    assertRejectedWith(
        scenarioWithFaults(List.of("BRAKE_WEAR")),
        "faults[0]: must be an object (was 'BRAKE_WEAR')");
  }
}
