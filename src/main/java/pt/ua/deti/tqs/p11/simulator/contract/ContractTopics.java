package pt.ua.deti.tqs.p11.simulator.contract;

/** Topics of the contract, version 1 (contract/README.md, section Topics). */
public final class ContractTopics {

  private ContractTopics() {}

  public static String telemetry(String fleetId, String vehicleId) {
    return vehicle(fleetId, vehicleId) + "/telemetry";
  }

  /** Where the simulator reports what happened once to one vehicle. */
  public static String events(String fleetId, String vehicleId) {
    return vehicle(fleetId, vehicleId) + "/events";
  }

  /** Where the simulator answers the commands sent to one vehicle. */
  public static String responses(String fleetId, String vehicleId) {
    return vehicle(fleetId, vehicleId) + "/responses";
  }

  /** Subscription to every command for every vehicle of a fleet: '+' matches one level. */
  public static String commandsOfFleet(String fleetId) {
    return "fleet/" + fleetId + "/vehicles/+/commands/+";
  }

  private static String vehicle(String fleetId, String vehicleId) {
    return "fleet/" + fleetId + "/vehicles/" + vehicleId;
  }
}
