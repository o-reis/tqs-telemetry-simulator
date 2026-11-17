package pt.ua.deti.tqs.p11.simulator.config;

/**
 * Simulation parameters of a scenario.
 *
 * @param emissionIntervalSeconds seconds between two readings of a vehicle, greater than 0
 * @param seed seed of the run's random generator
 * @param faultProbability chance of a fault per reading, from 0 to 1
 * @param drainPercentPerMinute battery lost per minute while riding, zero or more
 * @param chargePercentPerMinute battery gained per minute while parked at a hub, zero or more
 * @param tripProbability chance per reading that a parked vehicle starts a trip, from 0 to 1
 * @param rideSpeedKmh speed of a vehicle during a trip, greater than 0
 */
public record SimulationParams(
    double emissionIntervalSeconds,
    long seed,
    double faultProbability,
    double drainPercentPerMinute,
    double chargePercentPerMinute,
    double tripProbability,
    double rideSpeedKmh) {

  public static final double DEFAULT_RIDE_SPEED_KMH = 15.0;

  /** A scenario without battery rates or trips: the battery stays constant and nobody rides. */
  public SimulationParams(double emissionIntervalSeconds, long seed, double faultProbability) {
    this(emissionIntervalSeconds, seed, faultProbability, 0, 0);
  }

  /** A scenario without trips: every vehicle stays parked at its hub. */
  public SimulationParams(
      double emissionIntervalSeconds,
      long seed,
      double faultProbability,
      double drainPercentPerMinute,
      double chargePercentPerMinute) {
    this(
        emissionIntervalSeconds,
        seed,
        faultProbability,
        drainPercentPerMinute,
        chargePercentPerMinute,
        0,
        DEFAULT_RIDE_SPEED_KMH);
  }
}
