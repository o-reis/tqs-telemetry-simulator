package pt.ua.deti.tqs.p11.simulator.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pt.ua.deti.tqs.p11.simulator.telemetry.Position;

class ScenarioLoaderTest {

  private static final String VALID =
      """
      fleetId: fleet-01
      simulation:
        emissionIntervalSeconds: 10
        seed: 42
        faultProbability: 0.01
      hubs:
        - id: hub-01
          name: Estação de Aveiro
          position: { lat: 40.6443, lon: -8.6455 }
      vehicles:
        - id: bike-0001
          type: E_BIKE
          hub: hub-01
          battery: 85
      """;

  private final ScenarioLoader loader = new ScenarioLoader(new ScenarioParser());

  private static InputStream stream(String yaml) {
    return new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
  }

  private ScenarioConfig load(String yaml) {
    return loader.load(stream(yaml), "test.yml");
  }

  private void assertRejected(String yaml, String message) {
    assertThatThrownBy(() -> load(yaml))
        .isInstanceOf(InvalidScenarioException.class)
        .hasMessage(message);
  }

  private void assertRejectedStartingWith(String yaml, String start) {
    assertThatThrownBy(() -> load(yaml))
        .isInstanceOf(InvalidScenarioException.class)
        .hasMessageStartingWith(start);
  }

  @Test
  void load_validYaml_returnsTheConfig() {
    ScenarioConfig config = load(VALID);

    assertThat(config)
        .isEqualTo(
            new ScenarioConfig(
                "fleet-01",
                new SimulationParams(10.0, 42L, 0.01),
                List.of(
                    new HubConfig("hub-01", "Estação de Aveiro", new Position(40.6443, -8.6455))),
                List.of(new VehicleConfig("bike-0001", VehicleType.E_BIKE, "hub-01", 85.0))));
  }

  @Test
  void load_decimalEmissionInterval_isAccepted() {
    ScenarioConfig config =
        load(VALID.replace("emissionIntervalSeconds: 10", "emissionIntervalSeconds: 0.5"));

    assertThat(config.simulation().emissionIntervalSeconds()).isEqualTo(0.5);
  }

  @Test
  void load_withBatteryRates_readsThem() {
    ScenarioConfig config =
        load(
            VALID.replace(
                "  faultProbability: 0.01",
                "  faultProbability: 0.01\n  drainPercentPerMinute: 0.5\n  chargePercentPerMinute: 1"));

    assertThat(config.simulation()).isEqualTo(new SimulationParams(10.0, 42L, 0.01, 0.5, 1.0));
  }

  @Test
  void load_ruleBroken_reportsTheProblemsAndWhereTheyCameFrom() {
    assertRejected(
        VALID.replace("battery: 85", "battery: 150"),
        "Invalid scenario in test.yml (1 problem):\n"
            + " - vehicles[0].battery: must be between 0 and 100 (was 150.0)");
  }

  @Test
  void load_malformedYaml_isRejected() {
    assertRejectedStartingWith(
        "fleetId: [unclosed", "Invalid scenario in test.yml (1 problem):\n - not valid YAML:");
  }

  @Test
  void load_duplicateKey_isRejected() {
    assertThatThrownBy(() -> load("fleetId: a\nfleetId: b\n"))
        .isInstanceOf(InvalidScenarioException.class)
        .hasMessageContaining("not valid YAML")
        .hasMessageContaining("duplicate key");
  }

  @Test
  void load_javaTagInYaml_isRejectedAndNeverInstantiated() {
    assertRejectedStartingWith(
        VALID.replace("fleet-01", "!!java.io.File \"/tmp/x\""),
        "Invalid scenario in test.yml (1 problem):\n - not valid YAML:");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "# only a comment\n"})
  void load_emptyFile_isRejected(String yaml) {
    assertRejected(yaml, "Invalid scenario in test.yml (1 problem):\n - the file is empty");
  }

  @ParameterizedTest
  @ValueSource(strings = {"- a\n- b\n", "just text", "42"})
  void load_topLevelNotAnObject_isRejected(String yaml) {
    assertRejected(
        yaml, "Invalid scenario in test.yml (1 problem):\n - the top level must be an object");
  }
}
