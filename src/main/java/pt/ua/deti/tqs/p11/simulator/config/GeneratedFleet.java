package pt.ua.deti.tqs.p11.simulator.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A large fleet made from a count, for load tests. Deterministic and without random numbers, so it
 * never moves the seeded sequence of a run:
 *
 * <ul>
 *   <li>ids {@code {prefix}-0001}, {@code {prefix}-0002}, … (at least four digits, all the same
 *       width);
 *   <li>hubs used in turn, in scenario order;
 *   <li>types alternating, starting with an e-bike;
 *   <li>batteries spread over every whole value from 20 to 100 %, so every vehicle can be booked
 *       (BR1).
 * </ul>
 */
public final class GeneratedFleet {
  /** Upper limit of {@code generatedVehicles.count}, so a typo cannot exhaust the memory. */
  public static final int MAX_COUNT = 100_000;

  public static final String DEFAULT_ID_PREFIX = "vehicle";

  private static final int MIN_DIGITS = 4;

  /** Same limit as the contract's identifiers. */
  private static final int MAX_ID_LENGTH = 64;

  /** BR1: the minimum battery to book a vehicle. */
  private static final int MIN_BATTERY = 20;

  /** 20 to 100 % inclusive. */
  private static final int BATTERY_VALUES = 81;

  /** Has no common divisor with 81, so the batteries go through all 81 values before repeating. */
  private static final int BATTERY_STEP = 37;

  private GeneratedFleet() {}

  /**
   * @throws IllegalArgumentException if the count is outside 1 to {@link #MAX_COUNT} or there are
   *     no hubs
   */
  public static List<VehicleConfig> vehicles(int count, String idPrefix, List<HubConfig> hubs) {
    if (count < 1 || count > MAX_COUNT) {
      throw new IllegalArgumentException("Count must be between 1 and " + MAX_COUNT + ": " + count);
    }
    if (hubs.isEmpty()) {
      throw new IllegalArgumentException("A generated fleet needs at least one hub");
    }
    String number = "%0" + digits(count) + "d";
    List<VehicleConfig> fleet = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      fleet.add(
          new VehicleConfig(
              idPrefix + "-" + String.format(Locale.ROOT, number, i + 1),
              i % 2 == 0 ? VehicleType.E_BIKE : VehicleType.E_SCOOTER,
              hubs.get(i % hubs.size()).id(),
              MIN_BATTERY + (i * BATTERY_STEP) % BATTERY_VALUES));
    }
    return List.copyOf(fleet);
  }

  /** The longest prefix that keeps every id of a fleet of this size within 64 characters. */
  public static int maxPrefixLength(int count) {
    return MAX_ID_LENGTH - 1 - digits(count);
  }

  private static int digits(int count) {
    return Math.max(MIN_DIGITS, String.valueOf(count).length());
  }
}
