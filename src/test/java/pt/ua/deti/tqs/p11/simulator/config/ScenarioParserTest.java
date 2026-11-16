package pt.ua.deti.tqs.p11.simulator.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pt.ua.deti.tqs.p11.simulator.telemetry.Position;

class ScenarioParserTest {

  private final ScenarioParser parser = new ScenarioParser();

  private static Map<String, Object> simulation(Object intervalSeconds, Object faultProbability) {
    return Map.of(
        "emissionIntervalSeconds",
        intervalSeconds,
        "seed",
        42,
        "faultProbability",
        faultProbability);
  }

  /** A scenario with one hub and one vehicle. Integers where a YAML file would give them. */
  private static Map<String, Object> scenario(
      Map<String, Object> simulation, Object lat, Object lon, Object battery) {
    return Map.of(
        "fleetId",
        "fleet-01",
        "simulation",
        simulation,
        "hubs",
        List.of(
            Map.of(
                "id", "hub-01",
                "name", "Estação de Aveiro",
                "position", Map.of("lat", lat, "lon", lon))),
        "vehicles",
        List.of(Map.of("id", "bike-0001", "type", "E_BIKE", "hub", "hub-01", "battery", battery)));
  }

  private static Map<String, Object> scenarioWithBattery(Object battery) {
    return scenario(simulation(10, 0.01), 40.6443, -8.6455, battery);
  }

  private static Map<String, Object> scenarioWithLat(Object lat) {
    return scenario(simulation(10, 0.01), lat, -8.6455, 85);
  }

  private static Map<String, Object> scenarioWithLon(Object lon) {
    return scenario(simulation(10, 0.01), 40.6443, lon, 85);
  }

  private static Map<String, Object> scenarioWithInterval(Object intervalSeconds) {
    return scenario(simulation(intervalSeconds, 0.01), 40.6443, -8.6455, 85);
  }

  private static Map<String, Object> scenarioWithFaultProbability(Object faultProbability) {
    return scenario(simulation(10, faultProbability), 40.6443, -8.6455, 85);
  }

  private void assertRejectedWith(Map<String, Object> raw, String... problems) {
    assertThatThrownBy(() -> parser.parse(raw))
        .isInstanceOfSatisfying(
            InvalidScenarioException.class,
            e -> assertThat(e.getProblems()).containsExactly(problems));
  }

  @Test
  void parse_minimalValidScenario_returnsTheConfig() {
    ScenarioConfig config = parser.parse(scenarioWithBattery(85));

    assertThat(config)
        .isEqualTo(
            new ScenarioConfig(
                "fleet-01",
                new SimulationParams(10.0, 42L, 0.01),
                List.of(
                    new HubConfig("hub-01", "Estação de Aveiro", new Position(40.6443, -8.6455))),
                List.of(new VehicleConfig("bike-0001", VehicleType.E_BIKE, "hub-01", 85.0))));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 100})
  void parse_batteryAtTheLimits_isAccepted(int battery) {
    ScenarioConfig config = parser.parse(scenarioWithBattery(battery));

    assertThat(config.vehicles())
        .extracting(VehicleConfig::battery)
        .containsExactly((double) battery);
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 101})
  void parse_batteryOutsideTheRange_isRejectedWithTheFieldPath(int battery) {
    assertRejectedWith(
        scenarioWithBattery(battery),
        "vehicles[0].battery: must be between 0 and 100 (was " + (double) battery + ")");
  }

  @ParameterizedTest
  @ValueSource(doubles = {-90, 90})
  void parse_latitudeAtTheLimits_isAccepted(double lat) {
    ScenarioConfig config = parser.parse(scenarioWithLat(lat));

    assertThat(config.hubs()).extracting(h -> h.position().lat()).containsExactly(lat);
  }

  @ParameterizedTest
  @ValueSource(doubles = {-90.0001, 90.0001, Double.NaN})
  void parse_latitudeOutsideTheRange_isRejectedWithTheFieldPath(double lat) {
    assertRejectedWith(
        scenarioWithLat(lat), "hubs[0].position.lat: must be between -90 and 90 (was " + lat + ")");
  }

  @ParameterizedTest
  @ValueSource(doubles = {-180, 180})
  void parse_longitudeAtTheLimits_isAccepted(double lon) {
    ScenarioConfig config = parser.parse(scenarioWithLon(lon));

    assertThat(config.hubs()).extracting(h -> h.position().lon()).containsExactly(lon);
  }

  @ParameterizedTest
  @ValueSource(doubles = {-180.0001, 180.0001, Double.NaN})
  void parse_longitudeOutsideTheRange_isRejectedWithTheFieldPath(double lon) {
    assertRejectedWith(
        scenarioWithLon(lon),
        "hubs[0].position.lon: must be between -180 and 180 (was " + lon + ")");
  }

  @ParameterizedTest
  @ValueSource(doubles = {0, 1})
  void parse_faultProbabilityAtTheLimits_isAccepted(double probability) {
    ScenarioConfig config = parser.parse(scenarioWithFaultProbability(probability));

    assertThat(config.simulation().faultProbability()).isEqualTo(probability);
  }

  @ParameterizedTest
  @ValueSource(doubles = {-0.0001, 1.0001, Double.NaN})
  void parse_faultProbabilityOutsideTheRange_isRejectedWithTheFieldPath(double probability) {
    assertRejectedWith(
        scenarioWithFaultProbability(probability),
        "simulation.faultProbability: must be between 0 and 1 (was " + probability + ")");
  }

  @ParameterizedTest
  @ValueSource(doubles = {0.001, 10})
  void parse_positiveEmissionInterval_isAccepted(double intervalSeconds) {
    ScenarioConfig config = parser.parse(scenarioWithInterval(intervalSeconds));

    assertThat(config.simulation().emissionIntervalSeconds()).isEqualTo(intervalSeconds);
  }

  @ParameterizedTest
  @ValueSource(doubles = {0, -1, Double.NaN})
  void parse_emissionIntervalNotPositive_isRejectedWithTheFieldPath(double intervalSeconds) {
    assertRejectedWith(
        scenarioWithInterval(intervalSeconds),
        "simulation.emissionIntervalSeconds: must be greater than 0 (was " + intervalSeconds + ")");
  }

  @Test
  void parse_severalBrokenRules_reportsAllOfThemInFileOrder() {
    Map<String, Object> raw = scenario(simulation(0, 1.5), 91.0, -200.0, 101);

    assertRejectedWith(
        raw,
        "simulation.emissionIntervalSeconds: must be greater than 0 (was 0.0)",
        "simulation.faultProbability: must be between 0 and 1 (was 1.5)",
        "hubs[0].position.lat: must be between -90 and 90 (was 91.0)",
        "hubs[0].position.lon: must be between -180 and 180 (was -200.0)",
        "vehicles[0].battery: must be between 0 and 100 (was 101.0)");
  }
}
