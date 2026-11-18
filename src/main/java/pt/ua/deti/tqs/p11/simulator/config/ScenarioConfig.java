package pt.ua.deti.tqs.p11.simulator.config;

import java.util.List;

/**
 * @param faults faults raised at set moments, in file order; empty when the scenario has none
 */
public record ScenarioConfig(
    String fleetId,
    SimulationParams simulation,
    List<HubConfig> hubs,
    List<VehicleConfig> vehicles,
    List<ScriptedFault> faults) {

  /** A scenario without scripted faults. */
  public ScenarioConfig(
      String fleetId,
      SimulationParams simulation,
      List<HubConfig> hubs,
      List<VehicleConfig> vehicles) {
    this(fleetId, simulation, hubs, vehicles, List.of());
  }
}
