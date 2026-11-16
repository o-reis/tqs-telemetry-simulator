package pt.ua.deti.tqs.p11.simulator.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import pt.ua.deti.tqs.p11.simulator.telemetry.Position;

class ScenarioParserTest {

  private static final String ID_RULE = "[A-Za-z0-9_-]{1,64}";

  private final ScenarioParser parser = new ScenarioParser();

  // ---- Builders. Numbers are Integers where a YAML file would give them; null means absent. ----

  private static Map<String, Object> obj(Object... keysAndValues) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      if (keysAndValues[i + 1] != null) {
        map.put((String) keysAndValues[i], keysAndValues[i + 1]);
      }
    }
    return map;
  }

  private static Map<String, Object> position(Object lat, Object lon) {
    return obj("lat", lat, "lon", lon);
  }

  private static Map<String, Object> simulation(Object interval, Object seed, Object probability) {
    return obj("emissionIntervalSeconds", interval, "seed", seed, "faultProbability", probability);
  }

  private static Map<String, Object> hub(Object id, Object name, Object position) {
    return obj("id", id, "name", name, "position", position);
  }

  private static Map<String, Object> vehicle(Object id, Object type, Object hub, Object battery) {
    return obj("id", id, "type", type, "hub", hub, "battery", battery);
  }

  private static Map<String, Object> scenario(
      Object fleetId, Object simulation, Object hubs, Object vehicles) {
    return obj("fleetId", fleetId, "simulation", simulation, "hubs", hubs, "vehicles", vehicles);
  }

  private static Map<String, Object> validPosition() {
    return position(40.6443, -8.6455);
  }

  private static Map<String, Object> validSimulation() {
    return simulation(10, 42, 0.01);
  }

  private static Map<String, Object> validHub() {
    return hub("hub-01", "Estação de Aveiro", validPosition());
  }

  private static Map<String, Object> validVehicle() {
    return vehicle("bike-0001", "E_BIKE", "hub-01", 85);
  }

  private static Map<String, Object> withFleetId(Object fleetId) {
    return scenario(fleetId, validSimulation(), List.of(validHub()), List.of(validVehicle()));
  }

  private static Map<String, Object> withSimulation(Object simulation) {
    return scenario("fleet-01", simulation, List.of(validHub()), List.of(validVehicle()));
  }

  private static Map<String, Object> withHubs(Object hubs) {
    return scenario("fleet-01", validSimulation(), hubs, List.of(validVehicle()));
  }

  private static Map<String, Object> withVehicles(Object vehicles) {
    return scenario("fleet-01", validSimulation(), List.of(validHub()), vehicles);
  }

  private static Map<String, Object> scenarioWithBattery(Object battery) {
    return withVehicles(List.of(vehicle("bike-0001", "E_BIKE", "hub-01", battery)));
  }

  private static Map<String, Object> scenarioWithLat(Object lat) {
    return withHubs(List.of(hub("hub-01", "Estação de Aveiro", position(lat, -8.6455))));
  }

  private static Map<String, Object> scenarioWithLon(Object lon) {
    return withHubs(List.of(hub("hub-01", "Estação de Aveiro", position(40.6443, lon))));
  }

  private static Map<String, Object> scenarioWithInterval(Object interval) {
    return withSimulation(simulation(interval, 42, 0.01));
  }

  private static Map<String, Object> scenarioWithFaultProbability(Object probability) {
    return withSimulation(simulation(10, 42, probability));
  }

  private void assertRejectedWith(Map<String, Object> raw, String... problems) {
    assertThatThrownBy(() -> parser.parse(raw))
        .isInstanceOfSatisfying(
            InvalidScenarioException.class,
            e -> assertThat(e.getProblems()).containsExactly(problems));
  }

  // ---- Valid scenarios ----

  @Test
  void parse_minimalValidScenario_returnsTheConfig() {
    ScenarioConfig config = parser.parse(validScenario());

    assertThat(config)
        .isEqualTo(
            new ScenarioConfig(
                "fleet-01",
                new SimulationParams(10.0, 42L, 0.01),
                List.of(
                    new HubConfig("hub-01", "Estação de Aveiro", new Position(40.6443, -8.6455))),
                List.of(new VehicleConfig("bike-0001", VehicleType.E_BIKE, "hub-01", 85.0))));
  }

  private static Map<String, Object> validScenario() {
    return withFleetId("fleet-01");
  }

  @ParameterizedTest
  @EnumSource(VehicleType.class)
  void parse_everyVehicleType_isAccepted(VehicleType type) {
    ScenarioConfig config =
        parser.parse(withVehicles(List.of(vehicle("v-1", type.name(), "hub-01", 50))));

    assertThat(config.vehicles()).extracting(VehicleConfig::type).containsExactly(type);
  }

  @Test
  void parse_seedAsLong_isAccepted() {
    ScenarioConfig config = parser.parse(withSimulation(simulation(10, Long.MAX_VALUE, 0.01)));

    assertThat(config.simulation().seed()).isEqualTo(Long.MAX_VALUE);
  }

  // ---- Ranges ----

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
    Map<String, Object> raw =
        scenario(
            "fleet-01",
            simulation(0, 42, 1.5),
            List.of(hub("hub-01", "Estação de Aveiro", position(91.0, -200.0))),
            List.of(vehicle("bike-0001", "E_BIKE", "hub-01", 101)));

    assertRejectedWith(
        raw,
        "simulation.emissionIntervalSeconds: must be greater than 0 (was 0.0)",
        "simulation.faultProbability: must be between 0 and 1 (was 1.5)",
        "hubs[0].position.lat: must be between -90 and 90 (was 91.0)",
        "hubs[0].position.lon: must be between -180 and 180 (was -200.0)",
        "vehicles[0].battery: must be between 0 and 100 (was 101.0)");
  }

  // ---- Required fields ----

  private static Arguments missing(Map<String, Object> raw, String path) {
    return Arguments.of(raw, path + ": is required");
  }

  static Stream<Arguments> missingFields() {
    return Stream.of(
        missing(withFleetId(null), "fleetId"),
        missing(withSimulation(null), "simulation"),
        missing(withSimulation(simulation(null, 42, 0.01)), "simulation.emissionIntervalSeconds"),
        missing(withSimulation(simulation(10, null, 0.01)), "simulation.seed"),
        missing(withSimulation(simulation(10, 42, null)), "simulation.faultProbability"),
        missing(withHubs(null), "hubs"),
        missing(withHubs(List.of(hub(null, "Estação", validPosition()))), "hubs[0].id"),
        missing(withHubs(List.of(hub("hub-01", null, validPosition()))), "hubs[0].name"),
        missing(withHubs(List.of(hub("hub-01", "Estação", null))), "hubs[0].position"),
        missing(
            withHubs(List.of(hub("hub-01", "Estação", position(null, -8.6455)))),
            "hubs[0].position.lat"),
        missing(
            withHubs(List.of(hub("hub-01", "Estação", position(40.6443, null)))),
            "hubs[0].position.lon"),
        missing(withVehicles(null), "vehicles"),
        missing(withVehicles(List.of(vehicle(null, "E_BIKE", "hub-01", 85))), "vehicles[0].id"),
        missing(withVehicles(List.of(vehicle("b-1", null, "hub-01", 85))), "vehicles[0].type"),
        missing(withVehicles(List.of(vehicle("b-1", "E_BIKE", null, 85))), "vehicles[0].hub"),
        missing(
            withVehicles(List.of(vehicle("b-1", "E_BIKE", "hub-01", null))),
            "vehicles[0].battery"));
  }

  @ParameterizedTest(name = "{1}")
  @MethodSource("missingFields")
  void parse_missingRequiredField_isRejectedWithTheFieldPath(
      Map<String, Object> raw, String problem) {
    assertRejectedWith(raw, problem);
  }

  @ParameterizedTest
  @ValueSource(strings = {"hubs", "vehicles"})
  void parse_emptyList_isRejected(String list) {
    Map<String, Object> raw = list.equals("hubs") ? withHubs(List.of()) : withVehicles(List.of());

    assertRejectedWith(raw, list + ": must have at least one entry");
  }

  // ---- Wrong value types ----

  private static Arguments wrongType(Map<String, Object> raw, String problem) {
    return Arguments.of(raw, problem);
  }

  static Stream<Arguments> wrongTypes() {
    return Stream.of(
        wrongType(withFleetId(123), "fleetId: must be text (was 123)"),
        wrongType(withSimulation("fast"), "simulation: must be an object (was 'fast')"),
        wrongType(
            withSimulation(simulation("ten", 42, 0.01)),
            "simulation.emissionIntervalSeconds: must be a number (was 'ten')"),
        wrongType(
            withSimulation(simulation(10, 4.5, 0.01)),
            "simulation.seed: must be a whole number (was 4.5)"),
        wrongType(
            withSimulation(simulation(10, 42, true)),
            "simulation.faultProbability: must be a number (was true)"),
        wrongType(withHubs("none"), "hubs: must be a list (was 'none')"),
        wrongType(withHubs(List.of("hub-01")), "hubs[0]: must be an object (was 'hub-01')"),
        wrongType(
            withHubs(List.of(hub("hub-01", 7, validPosition()))),
            "hubs[0].name: must be text (was 7)"),
        wrongType(
            withHubs(List.of(hub("hub-01", "Estação", "here"))),
            "hubs[0].position: must be an object (was 'here')"),
        wrongType(
            withHubs(List.of(hub("hub-01", "Estação", position("north", -8.6455)))),
            "hubs[0].position.lat: must be a number (was 'north')"),
        wrongType(withVehicles("none"), "vehicles: must be a list (was 'none')"),
        wrongType(withVehicles(List.of(5)), "vehicles[0]: must be an object (was 5)"),
        wrongType(
            withVehicles(List.of(vehicle("b-1", 5, "hub-01", 85))),
            "vehicles[0].type: must be text (was 5)"),
        wrongType(
            withVehicles(List.of(vehicle("b-1", "E_BIKE", "hub-01", "alto"))),
            "vehicles[0].battery: must be a number (was 'alto')"),
        wrongType(
            withVehicles(List.of(vehicle("b-1", "E_BIKE", "hub-01", true))),
            "vehicles[0].battery: must be a number (was true)"));
  }

  @ParameterizedTest(name = "{1}")
  @MethodSource("wrongTypes")
  void parse_wrongValueType_isRejectedWithTheFieldPath(Map<String, Object> raw, String problem) {
    assertRejectedWith(raw, problem);
  }

  // ---- Identifiers ----

  @ParameterizedTest
  @ValueSource(strings = {"", "fleet/01", "fleet+01", "fleet#01", "fleet 01"})
  void parse_fleetIdUnsafeInATopic_isRejected(String fleetId) {
    assertRejectedWith(
        withFleetId(fleetId), "fleetId: must match " + ID_RULE + " (was '" + fleetId + "')");
  }

  @Test
  void parse_identifierOf64Characters_isAccepted_andOf65_isRejected() {
    String id64 = "a".repeat(64);
    String id65 = "a".repeat(65);

    assertThat(parser.parse(withFleetId(id64)).fleetId()).isEqualTo(id64);
    assertRejectedWith(
        withFleetId(id65), "fleetId: must match " + ID_RULE + " (was '" + id65 + "')");
  }

  @Test
  void parse_hubIdUnsafeInATopic_isRejected() {
    assertRejectedWith(
        withHubs(List.of(hub("hub/01", "Estação", validPosition()))),
        "hubs[0].id: must match " + ID_RULE + " (was 'hub/01')");
  }

  @Test
  void parse_vehicleIdUnsafeInATopic_isRejected() {
    assertRejectedWith(
        withVehicles(List.of(vehicle("bike 1", "E_BIKE", "hub-01", 85))),
        "vehicles[0].id: must match " + ID_RULE + " (was 'bike 1')");
  }

  @Test
  void parse_vehicleHubUnsafeInATopic_isRejectedAsAnIdAndNotAsUnknown() {
    assertRejectedWith(
        withVehicles(List.of(vehicle("bike-1", "E_BIKE", "hub#1", 85))),
        "vehicles[0].hub: must match " + ID_RULE + " (was 'hub#1')");
  }

  @Test
  void parse_blankHubName_isRejected() {
    assertRejectedWith(
        withHubs(List.of(hub("hub-01", "  ", validPosition()))), "hubs[0].name: must not be blank");
  }

  @Test
  void parse_duplicateHubId_isRejectedWithTheFirstIndex() {
    Map<String, Object> raw =
        withHubs(
            List.of(
                hub("hub-01", "A", validPosition()),
                hub("hub-02", "B", validPosition()),
                hub("hub-01", "C", validPosition())));

    assertRejectedWith(raw, "hubs[2].id: duplicate id 'hub-01' (first used in hubs[0])");
  }

  @Test
  void parse_duplicateVehicleId_isRejectedWithTheFirstIndex() {
    Map<String, Object> raw =
        withVehicles(
            List.of(
                vehicle("bike-0001", "E_BIKE", "hub-01", 85),
                vehicle("bike-0001", "E_SCOOTER", "hub-01", 60)));

    assertRejectedWith(raw, "vehicles[1].id: duplicate id 'bike-0001' (first used in vehicles[0])");
  }

  // ---- References and types ----

  @Test
  void parse_vehicleInAnUnknownHub_isRejectedListingTheKnownHubs() {
    Map<String, Object> raw =
        scenario(
            "fleet-01",
            validSimulation(),
            List.of(validHub(), hub("hub-02", "Fórum Aveiro", validPosition())),
            List.of(vehicle("bike-0001", "E_BIKE", "hub-99", 85)));

    assertRejectedWith(raw, "vehicles[0].hub: unknown hub 'hub-99' (known: hub-01, hub-02)");
  }

  @Test
  void parse_unknownHubWhileTheHubsAreBroken_reportsOnlyTheHubProblems() {
    Map<String, Object> raw =
        scenario(
            "fleet-01",
            validSimulation(),
            List.of(hub("hub-01", "Estação de Aveiro", position(91.0, -8.6455))),
            List.of(vehicle("bike-0001", "E_BIKE", "hub-99", 85)));

    assertRejectedWith(raw, "hubs[0].position.lat: must be between -90 and 90 (was 91.0)");
  }

  @ParameterizedTest
  @ValueSource(strings = {"E_CAR", "e_bike", ""})
  void parse_unknownVehicleType_isRejectedListingTheKnownTypes(String type) {
    assertRejectedWith(
        withVehicles(List.of(vehicle("bike-0001", type, "hub-01", 85))),
        "vehicles[0].type: unknown type '" + type + "' (known: E_BIKE, E_SCOOTER)");
  }
}
