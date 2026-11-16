package pt.ua.deti.tqs.p11.simulator.config;

import java.util.List;

public record ScenarioConfig(
    String fleetId,
    SimulationParams simulation,
    List<HubConfig> hubs,
    List<VehicleConfig> vehicles) {}
