package pt.ua.deti.tqs.p11.simulator.config;

import java.util.OptionalDouble;

/**
 * A fault the scenario file raises on a given vehicle at a given moment, so that tests do not
 * depend on chance. Times count simulated seconds from the start of the run.
 *
 * @param vehicleId vehicle that gets the fault
 * @param faultCode fault to raise, one of the codes of the simulator
 * @param atSeconds when to raise it, zero or more; if the vehicle already has a fault then, the
 *     scripted one is raised as soon as the vehicle is healthy
 * @param clearAfterSeconds how long after it was raised the fault goes away, greater than 0; empty
 *     means it stays for the rest of the run
 */
public record ScriptedFault(
    String vehicleId, String faultCode, double atSeconds, OptionalDouble clearAfterSeconds) {}
