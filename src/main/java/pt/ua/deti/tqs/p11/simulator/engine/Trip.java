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
  private static final double EARTH_RADIUS_KM = 6371.0;
  private static final double SECONDS_PER_HOUR = 3600.0;

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
   * Plans a trip between two hubs at a constant speed: it takes the distance between them divided
   * by the speed.
   *
   * @param speedKmh riding speed, greater than 0 and finite
   * @throws IllegalArgumentException if the speed is invalid or both hubs are at the same place
   */
  public static Trip between(Position origin, Position destination, double speedKmh) {
    // Written so that NaN is rejected too (every comparison with NaN is false).
    if (!(speedKmh > 0) || Double.isInfinite(speedKmh)) {
      throw new IllegalArgumentException("Speed must be greater than 0 and finite: " + speedKmh);
    }
    double distanceKm = distanceKm(origin, destination);
    if (distanceKm == 0) {
      throw new IllegalArgumentException("Origin and destination are the same place: " + origin);
    }
    return new Trip(origin, destination, distanceKm / speedKmh * SECONDS_PER_HOUR);
  }

  /**
   * Distance in km along the surface of the Earth (Haversine formula). Degrees of latitude and
   * longitude cannot be subtracted directly: a degree of longitude gets shorter away from the
   * equator.
   */
  public static double distanceKm(Position a, Position b) {
    double lat1 = Math.toRadians(a.lat());
    double lat2 = Math.toRadians(b.lat());
    double deltaLat = lat2 - lat1;
    double deltaLon = Math.toRadians(b.lon() - a.lon());
    double h =
        square(Math.sin(deltaLat / 2))
            + Math.cos(lat1) * Math.cos(lat2) * square(Math.sin(deltaLon / 2));
    return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(h));
  }

  private static double square(double x) {
    return x * x;
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
