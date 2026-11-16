package pt.ua.deti.tqs.p11.simulator.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pt.ua.deti.tqs.p11.simulator.telemetry.Position;

class ScenarioParserTest {

  private final ScenarioParser parser = new ScenarioParser();

  @Test
  void parse_minimalValidScenario_returnsTheConfig() {
    // Integers on purpose: that is what a YAML file gives for "10" and "42".
    Map<String, Object> raw =
        Map.of(
            "fleetId", "fleet-01",
            "simulation",
                Map.of("emissionIntervalSeconds", 10, "seed", 42, "faultProbability", 0.01),
            "hubs",
                List.of(
                    Map.of(
                        "id", "hub-01",
                        "name", "Estação de Aveiro",
                        "position", Map.of("lat", 40.6443, "lon", -8.6455))),
            "vehicles",
                List.of(
                    Map.of(
                        "id", "bike-0001",
                        "type", "E_BIKE",
                        "hub", "hub-01",
                        "battery", 85)));

    ScenarioConfig config = parser.parse(raw);

    assertThat(config)
        .isEqualTo(
            new ScenarioConfig(
                "fleet-01",
                new SimulationParams(10.0, 42L, 0.01),
                List.of(
                    new HubConfig("hub-01", "Estação de Aveiro", new Position(40.6443, -8.6455))),
                List.of(new VehicleConfig("bike-0001", VehicleType.E_BIKE, "hub-01", 85.0))));
  }
}
