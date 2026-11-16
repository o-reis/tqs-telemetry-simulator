package pt.ua.deti.tqs.p11.simulator.config;

import pt.ua.deti.tqs.p11.simulator.telemetry.Position;

public record HubConfig(String id, String name, Position position) {}
