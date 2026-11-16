package pt.ua.deti.tqs.p11.simulator.contract;

/** Topics of the contract, version 1 (contract/README.md, section Topics). */
public final class ContractTopics {

  private ContractTopics() {}

  public static String telemetry(String fleetId, String vehicleId) {
    return "fleet/" + fleetId + "/vehicles/" + vehicleId + "/telemetry";
  }
}
