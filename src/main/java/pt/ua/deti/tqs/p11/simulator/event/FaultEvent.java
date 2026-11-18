package pt.ua.deti.tqs.p11.simulator.event;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A fault that appeared on a vehicle, or went away. It says what happened; the moment is taken when
 * the event is turned into a message.
 *
 * @param faultCode the fault concerned, in UPPER_SNAKE_CASE like the contract requires
 */
public record FaultEvent(
    String fleetId, String vehicleId, FaultEventType type, String faultCode, Severity severity) {

  private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]*");

  public FaultEvent {
    Objects.requireNonNull(fleetId, "fleetId");
    Objects.requireNonNull(vehicleId, "vehicleId");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(severity, "severity");
    if (faultCode == null || !CODE.matcher(faultCode).matches()) {
      throw new IllegalArgumentException("Fault code must be UPPER_SNAKE_CASE: " + faultCode);
    }
  }
}
