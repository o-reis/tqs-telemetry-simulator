package pt.ua.deti.tqs.p11.simulator.config;

public record SimulationParams(
    double emissionIntervalSeconds, long seed, double faultProbability) {}
