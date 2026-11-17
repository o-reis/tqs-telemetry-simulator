package pt.ua.deti.tqs.p11.simulator.engine;

/**
 * Battery of a simulated vehicle: drains while it is ridden and charges while it is parked and
 * locked at a hub. Pure: no Spring, no real time, no randomness.
 *
 * @param drainPercentPerMinute battery lost per minute while riding, zero or more
 * @param chargePercentPerMinute battery gained per minute while parked at a hub, zero or more
 */
public record BatteryModel(double drainPercentPerMinute, double chargePercentPerMinute) {

  private static final double SECONDS_PER_MINUTE = 60.0;
  private static final double MIN_PERCENT = 0.0;
  private static final double MAX_PERCENT = 100.0;

  /** What a vehicle is doing during one tick. */
  public enum Activity {
    /** Unlocked and outside a hub: the battery drains. */
    RIDING,
    /** Locked at a hub: the battery charges. */
    PARKED_AT_HUB,
    /** Any other state: the battery does not change. */
    OTHER
  }

  public BatteryModel {
    requireValidRate("drainPercentPerMinute", drainPercentPerMinute);
    requireValidRate("chargePercentPerMinute", chargePercentPerMinute);
  }

  /**
   * Battery level after {@code elapsedSeconds} of the given activity, always between 0 and 100.
   *
   * @throws IllegalArgumentException if the battery is outside 0 to 100 or the time is negative
   */
  public double next(double batteryPercent, Activity activity, double elapsedSeconds) {
    // Written so that NaN is rejected too (every comparison with NaN is false).
    if (!(batteryPercent >= MIN_PERCENT && batteryPercent <= MAX_PERCENT)) {
      throw new IllegalArgumentException("Battery must be between 0 and 100: " + batteryPercent);
    }
    if (!(elapsedSeconds >= 0)) {
      throw new IllegalArgumentException("Elapsed time cannot be negative: " + elapsedSeconds);
    }
    double minutes = elapsedSeconds / SECONDS_PER_MINUTE;
    double change =
        switch (activity) {
          case RIDING -> -drainPercentPerMinute * minutes;
          case PARKED_AT_HUB -> chargePercentPerMinute * minutes;
          case OTHER -> 0.0;
        };
    return Math.clamp(batteryPercent + change, MIN_PERCENT, MAX_PERCENT);
  }

  private static void requireValidRate(String name, double rate) {
    if (!(rate >= 0) || Double.isInfinite(rate)) {
      throw new IllegalArgumentException(name + " must be zero or more and finite: " + rate);
    }
  }
}
