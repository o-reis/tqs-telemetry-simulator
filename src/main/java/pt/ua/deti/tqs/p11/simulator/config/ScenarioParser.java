package pt.ua.deti.tqs.p11.simulator.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.regex.Pattern;
import pt.ua.deti.tqs.p11.simulator.event.FaultCatalog;
import pt.ua.deti.tqs.p11.simulator.telemetry.Position;

/**
 * Turns the raw content of a scenario file (nested maps and lists) into a validated config. Reads
 * every value with a check, so a wrong or missing value becomes a problem with the path of its
 * field and never a ClassCastException or a NullPointerException.
 */
public class ScenarioParser {

  /** Same rule as the contract (envelope.schema.json): safe inside an MQTT topic. */
  private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9_-]{1,64}");

  /**
   * @throws InvalidScenarioException with every problem found, each with the path of its field
   */
  public ScenarioConfig parse(Map<?, ?> raw) {
    List<String> problems = new ArrayList<>();

    String fleetId = identifier(raw, "fleetId", "fleetId", problems);
    SimulationParams simulation = parseSimulation(raw, problems);

    int problemsBeforeHubs = problems.size();
    List<HubConfig> hubs = parseHubs(raw, problems);
    // Hub references are checked only against a valid hub list. Otherwise one broken hub would
    // also be reported as "unknown hub" in every vehicle that uses it.
    List<HubConfig> validHubs = problems.size() == problemsBeforeHubs ? hubs : null;
    List<String> knownHubs =
        validHubs == null ? null : validHubs.stream().map(HubConfig::id).toList();

    int problemsBeforeVehicles = problems.size();
    List<VehicleConfig> vehicles = parseAllVehicles(raw, knownHubs, validHubs, problems);
    // Same reason as for the hubs: check vehicle references only against a valid vehicle list.
    List<String> knownVehicles =
        problems.size() == problemsBeforeVehicles
            ? vehicles.stream().map(VehicleConfig::id).toList()
            : null;

    List<ScriptedFault> faults = parseFaults(raw, knownVehicles, problems);

    if (!problems.isEmpty()) {
      throw new InvalidScenarioException(problems);
    }
    return new ScenarioConfig(fleetId, simulation, hubs, vehicles, faults);
  }

  // The parse methods below return null (or leave the entry out) when they record a problem.
  // That never escapes: parse() throws before returning if there is any problem.

  private static SimulationParams parseSimulation(Map<?, ?> raw, List<String> problems) {
    Map<?, ?> simulation = object(raw, "simulation", "simulation", problems);
    if (simulation == null) {
      return null;
    }
    int before = problems.size();
    Double interval =
        number(
            simulation, "emissionIntervalSeconds", "simulation.emissionIntervalSeconds", problems);
    // Written so that NaN is rejected too (every comparison with NaN is false).
    if (interval != null && !(interval > 0)) {
      problems.add(
          "simulation.emissionIntervalSeconds: must be greater than 0 (was " + interval + ")");
    }
    Long seed = wholeNumber(simulation, "seed", "simulation.seed", problems);
    Double probability =
        number(simulation, "faultProbability", "simulation.faultProbability", problems);
    requireBetween(probability, 0, 1, "simulation.faultProbability", problems);
    Double drain =
        optionalRate(
            simulation, "drainPercentPerMinute", "simulation.drainPercentPerMinute", problems);
    Double charge =
        optionalRate(
            simulation, "chargePercentPerMinute", "simulation.chargePercentPerMinute", problems);
    Double tripProbability =
        optionalNumber(simulation, "tripProbability", "simulation.tripProbability", 0, problems);
    requireBetween(tripProbability, 0, 1, "simulation.tripProbability", problems);
    Double rideSpeed =
        optionalNumber(
            simulation,
            "rideSpeedKmh",
            "simulation.rideSpeedKmh",
            SimulationParams.DEFAULT_RIDE_SPEED_KMH,
            problems);
    // Written so that NaN is rejected too, and the upper bound rejects infinity.
    if (rideSpeed != null && !(rideSpeed > 0 && rideSpeed <= Double.MAX_VALUE)) {
      problems.add(
          "simulation.rideSpeedKmh: must be greater than 0 and finite (was " + rideSpeed + ")");
    }
    return problems.size() == before
        ? new SimulationParams(
            interval, seed, probability, drain, charge, tripProbability, rideSpeed)
        : null;
  }

  /**
   * Reads a rate that may be left out: absent means 0. A present value must be a number, zero or
   * more and finite.
   */
  private static Double optionalRate(
      Map<?, ?> parent, String key, String path, List<String> problems) {
    Double rate = optionalNumber(parent, key, path, 0, problems);
    // Written so that NaN is rejected too, and the upper bound rejects infinity.
    if (rate != null && !(rate >= 0 && rate <= Double.MAX_VALUE)) {
      problems.add(path + ": must be zero or more and finite (was " + rate + ")");
    }
    return rate;
  }

  /** Reads a number that may be left out: absent means {@code defaultValue}. */
  private static Double optionalNumber(
      Map<?, ?> parent, String key, String path, double defaultValue, List<String> problems) {
    if (parent.get(key) == null) {
      return defaultValue;
    }
    return number(parent, key, path, problems);
  }

  private static List<HubConfig> parseHubs(Map<?, ?> raw, List<String> problems) {
    List<HubConfig> hubs = new ArrayList<>();
    List<?> rawHubs = list(raw, "hubs", "hubs", problems);
    if (rawHubs == null) {
      return hubs;
    }
    Map<String, Integer> firstIndexById = new HashMap<>();
    for (int i = 0; i < rawHubs.size(); i++) {
      String path = "hubs[" + i + "]";
      Map<?, ?> hub = asObject(rawHubs.get(i), path, problems);
      if (hub == null) {
        continue;
      }
      int before = problems.size();
      String id = identifier(hub, "id", path + ".id", problems);
      rejectDuplicate(firstIndexById, id, i, "hubs", path + ".id", problems);
      String name = text(hub, "name", path + ".name", problems);
      if (name != null && name.isBlank()) {
        problems.add(path + ".name: must not be blank");
      }
      Position position =
          parsePosition(
              object(hub, "position", path + ".position", problems), path + ".position", problems);
      if (problems.size() == before) {
        hubs.add(new HubConfig(id, name, position));
      }
    }
    return hubs;
  }

  /**
   * Checks the range before building the Position, whose constructor would throw without the path
   * of the field.
   */
  private static Position parsePosition(Map<?, ?> position, String path, List<String> problems) {
    if (position == null) {
      return null;
    }
    int before = problems.size();
    Double lat = number(position, "lat", path + ".lat", problems);
    requireBetween(lat, -90, 90, path + ".lat", problems);
    Double lon = number(position, "lon", path + ".lon", problems);
    requireBetween(lon, -180, 180, path + ".lon", problems);
    return problems.size() == before ? new Position(lat, lon) : null;
  }

  /**
   * The listed vehicles, then the generated ones. {@code vehicles} may be left out when the
   * scenario has {@code generatedVehicles}.
   *
   * @param validHubs the hubs, or {@code null} if the hub list has problems
   */
  private static List<VehicleConfig> parseAllVehicles(
      Map<?, ?> raw, List<String> knownHubs, List<HubConfig> validHubs, List<String> problems) {
    boolean generates = raw.get("generatedVehicles") != null;
    List<VehicleConfig> vehicles =
        generates && raw.get("vehicles") == null
            ? new ArrayList<>()
            : parseVehicles(raw, knownHubs, problems);
    if (generates) {
      vehicles.addAll(parseGeneratedVehicles(raw, vehicles, validHubs, problems));
    }
    return vehicles;
  }

  private static List<VehicleConfig> parseVehicles(
      Map<?, ?> raw, List<String> knownHubs, List<String> problems) {
    List<VehicleConfig> vehicles = new ArrayList<>();
    List<?> rawVehicles = list(raw, "vehicles", "vehicles", problems);
    if (rawVehicles == null) {
      return vehicles;
    }
    Map<String, Integer> firstIndexById = new HashMap<>();
    for (int i = 0; i < rawVehicles.size(); i++) {
      String path = "vehicles[" + i + "]";
      Map<?, ?> vehicle = asObject(rawVehicles.get(i), path, problems);
      if (vehicle == null) {
        continue;
      }
      int before = problems.size();
      String id = identifier(vehicle, "id", path + ".id", problems);
      rejectDuplicate(firstIndexById, id, i, "vehicles", path + ".id", problems);
      String typeName = text(vehicle, "type", path + ".type", problems);
      VehicleType type = typeName == null ? null : vehicleType(typeName, path + ".type", problems);
      String hubId = identifier(vehicle, "hub", path + ".hub", problems);
      if (hubId != null && knownHubs != null && !knownHubs.contains(hubId)) {
        problems.add(
            path
                + ".hub: unknown hub '"
                + hubId
                + "' (known: "
                + String.join(", ", knownHubs)
                + ")");
      }
      Double battery = number(vehicle, "battery", path + ".battery", problems);
      requireBetween(battery, 0, 100, path + ".battery", problems);
      if (problems.size() == before) {
        vehicles.add(new VehicleConfig(id, type, hubId, battery));
      }
    }
    return vehicles;
  }

  /**
   * Reads {@code generatedVehicles} ({@code count}, optional {@code idPrefix}) and builds the fleet
   * with {@link GeneratedFleet}.
   *
   * @param listed the listed vehicles, whose ids the generated ones must not reuse
   * @param validHubs the hubs, or {@code null} if the hub list has problems (already reported)
   */
  private static List<VehicleConfig> parseGeneratedVehicles(
      Map<?, ?> raw, List<VehicleConfig> listed, List<HubConfig> validHubs, List<String> problems) {
    Map<?, ?> generated = asObject(raw.get("generatedVehicles"), "generatedVehicles", problems);
    if (generated == null) {
      return List.of();
    }
    int before = problems.size();
    Long count = wholeNumber(generated, "count", "generatedVehicles.count", problems);
    if (count != null && !(count >= 1 && count <= GeneratedFleet.MAX_COUNT)) {
      problems.add(
          "generatedVehicles.count: must be between 1 and "
              + GeneratedFleet.MAX_COUNT
              + " (was "
              + count
              + ")");
    }
    String prefix =
        generated.get("idPrefix") == null
            ? GeneratedFleet.DEFAULT_ID_PREFIX
            : identifier(generated, "idPrefix", "generatedVehicles.idPrefix", problems);
    if (problems.size() != before) {
      return List.of();
    }
    int size = count.intValue();
    int maxPrefix = GeneratedFleet.maxPrefixLength(size);
    if (prefix.length() > maxPrefix) {
      problems.add(
          "generatedVehicles.idPrefix: must have at most "
              + maxPrefix
              + " characters for "
              + size
              + " vehicles (was "
              + prefix.length()
              + ")");
      return List.of();
    }
    if (validHubs == null) {
      return List.of();
    }
    List<VehicleConfig> fleet = GeneratedFleet.vehicles(size, prefix, validHubs);
    Map<String, Integer> listedIndexById = new HashMap<>();
    for (int i = 0; i < listed.size(); i++) {
      listedIndexById.putIfAbsent(listed.get(i).id(), i);
    }
    // The first clash is enough: with a shared prefix, every later id would clash too.
    for (VehicleConfig vehicle : fleet) {
      Integer index = listedIndexById.get(vehicle.id());
      if (index != null) {
        problems.add(
            "generatedVehicles: id '"
                + vehicle.id()
                + "' is already used in vehicles["
                + index
                + "]");
        return List.of();
      }
    }
    return fleet;
  }

  /** The {@code faults} section is optional: absent or empty means no scripted faults. */
  private static List<ScriptedFault> parseFaults(
      Map<?, ?> raw, List<String> knownVehicles, List<String> problems) {
    List<ScriptedFault> faults = new ArrayList<>();
    Object value = raw.get("faults");
    if (value == null) {
      return faults;
    }
    if (!(value instanceof List<?> rawFaults)) {
      problems.add("faults: must be a list (was " + describe(value) + ")");
      return faults;
    }
    for (int i = 0; i < rawFaults.size(); i++) {
      String path = "faults[" + i + "]";
      Map<?, ?> fault = asObject(rawFaults.get(i), path, problems);
      if (fault == null) {
        continue;
      }
      int before = problems.size();
      String vehicleId = identifier(fault, "vehicle", path + ".vehicle", problems);
      if (vehicleId != null && knownVehicles != null && !knownVehicles.contains(vehicleId)) {
        problems.add(
            path
                + ".vehicle: unknown vehicle '"
                + vehicleId
                + "' (known: "
                + String.join(", ", knownVehicles)
                + ")");
      }
      String code = text(fault, "code", path + ".code", problems);
      if (code != null && !FaultCatalog.isKnown(code)) {
        problems.add(
            path
                + ".code: unknown fault code '"
                + code
                + "' (known: "
                + String.join(", ", FaultCatalog.CODES)
                + ")");
      }
      Double at = number(fault, "atSeconds", path + ".atSeconds", problems);
      // Written so that NaN is rejected too, and the upper bound rejects infinity.
      if (at != null && !(at >= 0 && at <= Double.MAX_VALUE)) {
        problems.add(path + ".atSeconds: must be zero or more and finite (was " + at + ")");
      }
      OptionalDouble clearAfter = optionalClearAfter(fault, path + ".clearAfterSeconds", problems);
      if (problems.size() == before) {
        faults.add(new ScriptedFault(vehicleId, code, at, clearAfter));
      }
    }
    return faults;
  }

  /** Reads {@code clearAfterSeconds}: absent means the fault stays for the rest of the run. */
  private static OptionalDouble optionalClearAfter(
      Map<?, ?> fault, String path, List<String> problems) {
    if (fault.get("clearAfterSeconds") == null) {
      return OptionalDouble.empty();
    }
    Double seconds = number(fault, "clearAfterSeconds", path, problems);
    if (seconds == null) {
      return OptionalDouble.empty();
    }
    if (!(seconds > 0 && seconds <= Double.MAX_VALUE)) {
      problems.add(path + ": must be greater than 0 and finite (was " + seconds + ")");
    }
    return OptionalDouble.of(seconds);
  }

  private static VehicleType vehicleType(String name, String path, List<String> problems) {
    for (VehicleType type : VehicleType.values()) {
      if (type.name().equals(name)) {
        return type;
      }
    }
    problems.add(
        path
            + ": unknown type '"
            + name
            + "' (known: "
            + String.join(
                ", ", java.util.Arrays.stream(VehicleType.values()).map(Enum::name).toList())
            + ")");
    return null;
  }

  private static void rejectDuplicate(
      Map<String, Integer> firstIndexById,
      String id,
      int index,
      String listName,
      String path,
      List<String> problems) {
    if (id == null) {
      return;
    }
    Integer first = firstIndexById.putIfAbsent(id, index);
    if (first != null) {
      problems.add(
          path + ": duplicate id '" + id + "' (first used in " + listName + "[" + first + "])");
    }
  }

  /** Adds a problem unless min <= value <= max. A null value was already reported. */
  private static void requireBetween(
      Double value, int min, int max, String path, List<String> problems) {
    if (value != null && !(value >= min && value <= max)) {
      problems.add(path + ": must be between " + min + " and " + max + " (was " + value + ")");
    }
  }

  // ---- Checked readers: return null and record a problem when the value is absent or wrong. ----

  private static Object required(Map<?, ?> parent, String key, String path, List<String> problems) {
    Object value = parent.get(key);
    if (value == null) {
      problems.add(path + ": is required");
    }
    return value;
  }

  private static Map<?, ?> object(
      Map<?, ?> parent, String key, String path, List<String> problems) {
    Object value = required(parent, key, path, problems);
    return value == null ? null : asObject(value, path, problems);
  }

  private static Map<?, ?> asObject(Object value, String path, List<String> problems) {
    if (value instanceof Map<?, ?> map) {
      return map;
    }
    problems.add(path + ": must be an object (was " + describe(value) + ")");
    return null;
  }

  private static List<?> list(Map<?, ?> parent, String key, String path, List<String> problems) {
    Object value = required(parent, key, path, problems);
    if (value == null) {
      return null;
    }
    if (!(value instanceof List<?> list)) {
      problems.add(path + ": must be a list (was " + describe(value) + ")");
      return null;
    }
    if (list.isEmpty()) {
      problems.add(path + ": must have at least one entry");
      return null;
    }
    return list;
  }

  private static String text(Map<?, ?> parent, String key, String path, List<String> problems) {
    Object value = required(parent, key, path, problems);
    if (value == null) {
      return null;
    }
    if (value instanceof String text) {
      return text;
    }
    problems.add(path + ": must be text (was " + describe(value) + ")");
    return null;
  }

  private static String identifier(
      Map<?, ?> parent, String key, String path, List<String> problems) {
    String value = text(parent, key, path, problems);
    if (value != null && !IDENTIFIER.matcher(value).matches()) {
      problems.add(path + ": must match " + IDENTIFIER + " (was '" + value + "')");
      return null;
    }
    return value;
  }

  private static Double number(Map<?, ?> parent, String key, String path, List<String> problems) {
    Object value = required(parent, key, path, problems);
    if (value == null) {
      return null;
    }
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    problems.add(path + ": must be a number (was " + describe(value) + ")");
    return null;
  }

  private static Long wholeNumber(
      Map<?, ?> parent, String key, String path, List<String> problems) {
    Object value = required(parent, key, path, problems);
    if (value == null) {
      return null;
    }
    if (value instanceof Integer || value instanceof Long) {
      return ((Number) value).longValue();
    }
    problems.add(path + ": must be a whole number (was " + describe(value) + ")");
    return null;
  }

  private static String describe(Object value) {
    return value instanceof String text ? "'" + text + "'" : String.valueOf(value);
  }
}
