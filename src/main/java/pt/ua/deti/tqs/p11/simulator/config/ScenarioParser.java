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

    SimulationParams simulation = parseSimulation(asMap(raw.get("simulation")), problems);
    List<Object> rawHubs = asList(raw.get("hubs"));
    List<HubConfig> hubs = new ArrayList<>();
    for (int i = 0; i < rawHubs.size(); i++) {
      hubs.add(parseHub(asMap(rawHubs.get(i)), "hubs[" + i + "]", problems));
    }
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

  private SimulationParams parseSimulation(Map<String, Object> sim, List<String> problems) {
    double interval = ((Number) sim.get("emissionIntervalSeconds")).doubleValue();
    // Written so that NaN is rejected too (every comparison with NaN is false).
    if (!(interval > 0)) {
      problems.add(
          "simulation.emissionIntervalSeconds: must be greater than 0 (was " + interval + ")");
    }
    double faultProbability = ((Number) sim.get("faultProbability")).doubleValue();
    requireBetween(faultProbability, 0, 1, "simulation.faultProbability", problems);
    return new SimulationParams(interval, ((Number) sim.get("seed")).longValue(), faultProbability);
  }

  private HubConfig parseHub(Map<String, Object> hub, String path, List<String> problems) {
    Position position = parsePosition(asMap(hub.get("position")), path + ".position", problems);
    return new HubConfig((String) hub.get("id"), (String) hub.get("name"), position);
  }

  /**
   * Checks the range before building the Position, whose constructor would throw without the path
   * of the field. Returns null when invalid; the problem is recorded and parse() throws at the end.
   */
  private Position parsePosition(Map<String, Object> position, String path, List<String> problems) {
    double lat = ((Number) position.get("lat")).doubleValue();
    double lon = ((Number) position.get("lon")).doubleValue();
    int problemsBefore = problems.size();
    requireBetween(lat, -90, 90, path + ".lat", problems);
    requireBetween(lon, -180, 180, path + ".lon", problems);
    return problems.size() == problemsBefore ? new Position(lat, lon) : null;
  }

  private VehicleConfig parseVehicle(
      Map<String, Object> vehicle, String path, List<String> problems) {
    double battery = ((Number) vehicle.get("battery")).doubleValue();
    requireBetween(battery, 0, 100, path + ".battery", problems);
    return new VehicleConfig(
        (String) vehicle.get("id"),
        VehicleType.valueOf((String) vehicle.get("type")),
        (String) vehicle.get("hub"),
        battery);
  }

  /** Adds a problem unless min <= value <= max. Written so that NaN is rejected too. */
  private static void requireBetween(
      double value, int min, int max, String path, List<String> problems) {
    if (!(value >= min && value <= max)) {
      problems.add(path + ": must be between " + min + " and " + max + " (was " + value + ")");
    }
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
