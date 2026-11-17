package pt.ua.deti.tqs.p11.simulator.engine;

/**
 * Battery of a simulated vehicle: drains while it is ridden and charges while it is parked and
 * locked at a hub. Pure: no Spring, no real time, no randomness.
 *
 * <p>STUB: only the API, so that the tests compile and fail. The behaviour comes in the next
 * commit.
 */
public record BatteryModel(double drainPercentPerMinute, double chargePercentPerMinute) {

  /** What a vehicle is doing during one tick. */
  public enum Activity {
    RIDING,
    PARKED_AT_HUB,
    OTHER
  }

  /**
   * Battery level after {@code elapsedSeconds} of the given activity, always between 0 and 100.
   *
   * @throws IllegalArgumentException if the battery is outside 0 to 100 or the time is negative
   */
  public double next(double batteryPercent, Activity activity, double elapsedSeconds) {
    throw new UnsupportedOperationException("not implemented yet");
  }
}
