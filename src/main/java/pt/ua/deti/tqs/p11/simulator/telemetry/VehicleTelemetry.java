package pt.ua.deti.tqs.p11.simulator.telemetry;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Current state of one vehicle, as the simulator reports it. The rules mirror the contract
 * (contract/README.md), so a value that breaks them never reaches the wire.
 */
public final class VehicleTelemetry {

  /** Safe inside an MQTT topic: no '/', '+' or '#'. */
  private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9_-]{1,64}");

  private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]*");

  private final String fleetId;
  private final String vehicleId;
  private final double batteryPercent;
  private final LockState lockState;
  private final Position position;
  private final String hubId;
  private final String faultCode;
  private final Double speedKmh;

  /**
   * Creates the state of a vehicle.
   *
   * @param position the position, or {@code null} if unknown
   * @param hubId the hub where the vehicle is parked, or {@code null} if it is not in a hub; at
   *     least one of position and hubId is required
   * @param faultCode the active fault in UPPER_SNAKE_CASE, or {@code null} if none
   * @param speedKmh the current speed, or {@code null} if not reported
   */
  public VehicleTelemetry(
      String fleetId,
      String vehicleId,
      double batteryPercent,
      LockState lockState,
      Position position,
      String hubId,
      String faultCode,
      Double speedKmh) {
    this.fleetId = requireIdentifier("fleetId", fleetId);
    this.vehicleId = requireIdentifier("vehicleId", vehicleId);
    if (!(batteryPercent >= 0 && batteryPercent <= 100)) {
      throw new IllegalArgumentException("Battery must be between 0 and 100: " + batteryPercent);
    }
    this.batteryPercent = batteryPercent;
    this.lockState = Objects.requireNonNull(lockState, "lockState");
    if (position == null && hubId == null) {
      throw new IllegalArgumentException("A vehicle needs a known position or a hub");
    }
    this.position = position;
    this.hubId = hubId == null ? null : requireIdentifier("hubId", hubId);
    if (faultCode != null && !CODE.matcher(faultCode).matches()) {
      throw new IllegalArgumentException("Fault code must be UPPER_SNAKE_CASE: " + faultCode);
    }
    this.faultCode = faultCode;
    if (speedKmh != null && !(speedKmh >= 0)) {
      throw new IllegalArgumentException("Speed cannot be negative: " + speedKmh);
    }
    this.speedKmh = speedKmh;
  }

  private static String requireIdentifier(String name, String value) {
    if (value == null || !IDENTIFIER.matcher(value).matches()) {
      throw new IllegalArgumentException(name + " must match " + IDENTIFIER + ": " + value);
    }
    return value;
  }

  public String fleetId() {
    return fleetId;
  }

  public String vehicleId() {
    return vehicleId;
  }

  public double batteryPercent() {
    return batteryPercent;
  }

  public LockState lockState() {
    return lockState;
  }

  public Optional<Position> position() {
    return Optional.ofNullable(position);
  }

  public Optional<String> hubId() {
    return Optional.ofNullable(hubId);
  }

  public Optional<String> faultCode() {
    return Optional.ofNullable(faultCode);
  }

  public Optional<Double> speedKmh() {
    return Optional.ofNullable(speedKmh);
  }
}
