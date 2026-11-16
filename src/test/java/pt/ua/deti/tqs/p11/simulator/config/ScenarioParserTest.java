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

  /** A valid scenario whose only vehicle has the given battery. Integers, as a YAML file gives. */
  private static Map<String, Object> scenarioWithBattery(Object battery) {
    return Map.of(
        "fleetId", "fleet-01",
        "simulation", Map.of("emissionIntervalSeconds", 10, "seed", 42, "faultProbability", 0.01),
        "hubs",
            List.of(
                Map.of(
                    "id", "hub-01",
                    "name", "Estação de Aveiro",
                    "position", Map.of("lat", 40.6443, "lon", -8.6455))),
        "vehicles",
            List.of(
                Map.of("id", "bike-0001", "type", "E_BIKE", "hub", "hub-01", "battery", battery)));
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
    Map<String, Object> raw = scenarioWithBattery(battery);

    assertThatThrownBy(() -> parser.parse(raw))
        .isInstanceOfSatisfying(
            InvalidScenarioException.class,
            e ->
                assertThat(e.getProblems())
                    .containsExactly(
                        "vehicles[0].battery: must be between 0 and 100 (was "
                            + (double) battery
                            + ")"));
  }
}
