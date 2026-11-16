package pt.ua.deti.tqs.p11.simulator.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import pt.ua.deti.tqs.p11.simulator.telemetry.Position;

/** Turns the raw content of a scenario file (nested maps and lists) into a validated config. */
public class ScenarioParser {

  /**
   * @throws InvalidScenarioException with every problem found, each with the path of its field
   */
  public ScenarioConfig parse(Map<String, Object> raw) {
    List<String> problems = new ArrayList<>();

    SimulationParams simulation = parseSimulation(asMap(raw.get("simulation")));
    List<HubConfig> hubs = asList(raw.get("hubs")).stream().map(h -> parseHub(asMap(h))).toList();
    List<Object> rawVehicles = asList(raw.get("vehicles"));
    List<VehicleConfig> vehicles = new ArrayList<>();
    for (int i = 0; i < rawVehicles.size(); i++) {
      vehicles.add(parseVehicle(asMap(rawVehicles.get(i)), "vehicles[" + i + "]", problems));
    }

    if (!problems.isEmpty()) {
      throw new InvalidScenarioException(problems);
    }
    return new ScenarioConfig((String) raw.get("fleetId"), simulation, hubs, vehicles);
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

  private VehicleConfig parseVehicle(
      Map<String, Object> vehicle, String path, List<String> problems) {
    double battery = ((Number) vehicle.get("battery")).doubleValue();
    // Written so that NaN is rejected too (every comparison with NaN is false).
    if (!(battery >= 0 && battery <= 100)) {
      problems.add(path + ".battery: must be between 0 and 100 (was " + battery + ")");
    }
    return new VehicleConfig(
        (String) vehicle.get("id"),
        VehicleType.valueOf((String) vehicle.get("type")),
        (String) vehicle.get("hub"),
        battery);
  }

  // Temporary: a later cycle replaces these casts with checked readers that report the
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
