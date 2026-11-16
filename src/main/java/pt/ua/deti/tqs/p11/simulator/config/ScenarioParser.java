package pt.ua.deti.tqs.p11.simulator.config;

import java.util.List;
import java.util.Map;
import pt.ua.deti.tqs.p11.simulator.telemetry.Position;

/** Turns the raw content of a scenario file (nested maps and lists) into a validated config. */
public class ScenarioParser {

  public ScenarioConfig parse(Map<String, Object> raw) {
    return new ScenarioConfig(
        (String) raw.get("fleetId"),
        parseSimulation(asMap(raw.get("simulation"))),
        asList(raw.get("hubs")).stream().map(h -> parseHub(asMap(h))).toList(),
        asList(raw.get("vehicles")).stream().map(v -> parseVehicle(asMap(v))).toList());
  }

  private SimulationParams parseSimulation(Map<String, Object> sim) {
    return new SimulationParams(
        ((Number) sim.get("emissionIntervalSeconds")).doubleValue(),
        ((Number) sim.get("seed")).longValue(),
        ((Number) sim.get("faultProbability")).doubleValue());
  }

  private HubConfig parseHub(Map<String, Object> hub) {
    Map<String, Object> position = asMap(hub.get("position"));
    return new HubConfig(
        (String) hub.get("id"),
        (String) hub.get("name"),
        new Position(
            ((Number) position.get("lat")).doubleValue(),
            ((Number) position.get("lon")).doubleValue()));
  }

  private VehicleConfig parseVehicle(Map<String, Object> vehicle) {
    return new VehicleConfig(
        (String) vehicle.get("id"),
        VehicleType.valueOf((String) vehicle.get("type")),
        (String) vehicle.get("hub"),
        ((Number) vehicle.get("battery")).doubleValue());
  }

  // Temporary: the next cycles replace these casts with checked readers that report the
  // field path instead of throwing ClassCastException (wrong value types, missing fields).
  @SuppressWarnings("unchecked")
  private static Map<String, Object> asMap(Object value) {
    return (Map<String, Object>) value;
  }

  @SuppressWarnings("unchecked")
  private static List<Object> asList(Object value) {
    return (List<Object>) value;
  }
}
