package pt.ua.deti.tqs.p11.simulator.config;

/**
 * Simulation parameters of a scenario.
 *
 * @param emissionIntervalSeconds seconds between two readings of a vehicle, greater than 0
 * @param seed seed of the run's random generator
 * @param faultProbability chance of a fault per reading, from 0 to 1
 * @param drainPercentPerMinute battery lost per minute while riding, zero or more
 * @param chargePercentPerMinute battery gained per minute while parked at a hub, zero or more
 */
public record SimulationParams(
    double emissionIntervalSeconds,
    long seed,
    double faultProbability,
    double drainPercentPerMinute,
    double chargePercentPerMinute) {

  /** A scenario without battery rates: the battery level stays constant. */
  public SimulationParams(double emissionIntervalSeconds, long seed, double faultProbability) {
    this(emissionIntervalSeconds, seed, faultProbability, 0, 0);
  }
}
