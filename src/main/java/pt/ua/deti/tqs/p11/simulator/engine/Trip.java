package pt.ua.deti.tqs.p11.simulator.engine;

import java.util.Objects;
import pt.ua.deti.tqs.p11.simulator.telemetry.Position;

/**
 * A ride from one hub to another, in a straight line and at constant speed. Pure: no Spring, no
 * real time, no randomness.
 *
 * <p>The position moves linearly from the origin to the destination, so it changes gradually and
 * never leaves the box formed by the two hubs. From {@code durationSeconds} on, the position is
 * exactly the destination.
 *
 * @param origin position of the hub where the trip starts
 * @param destination position of the hub where the trip ends
 * @param durationSeconds how long the trip takes, greater than 0 and finite
 */
public record Trip(Position origin, Position destination, double durationSeconds) {
  public Trip {
    Objects.requireNonNull(origin, "origin");
    Objects.requireNonNull(destination, "destination");
    // Written so that NaN is rejected too (every comparison with NaN is false).
    if (!(durationSeconds > 0) || Double.isInfinite(durationSeconds)) {
      throw new IllegalArgumentException(
          "Duration must be greater than 0 and finite: " + durationSeconds);
    }
  }

  /**
   * Position after {@code elapsedSeconds} of riding.
   *
   * @throws IllegalArgumentException if the time is negative or NaN
   */
  public Position positionAt(double elapsedSeconds) {
    if (!(elapsedSeconds >= 0)) {
      throw new IllegalArgumentException("Elapsed time cannot be negative: " + elapsedSeconds);
    }
    // Returned as is, not computed: the end must match the hub exactly, with no rounding error.
    if (isFinished(elapsedSeconds)) {
      return destination;
    }
    double fraction = elapsedSeconds / durationSeconds;
    return new Position(
        between(origin.lat(), destination.lat(), fraction),
        between(origin.lon(), destination.lon(), fraction));
  }

  public boolean isFinished(double elapsedSeconds) {
    return elapsedSeconds >= durationSeconds;
  }

  private static double between(double from, double to, double fraction) {
    return from + (to - from) * fraction;
  }
}