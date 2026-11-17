package pt.ua.deti.tqs.p11.simulator.engine;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * What happened to a command from the platform, as the contract's response reports it: a status
 * and, unless it was completed, a reason in {@code UPPER_SNAKE_CASE}.
 *
 * @param status what happened
 * @param reason why it was not completed; {@code null} exactly when the status is {@code
 *     COMPLETED}, as in the contract's response
 */
public record CommandOutcome(Status status, String reason) {

  /** The response statuses of the contract. */
  public enum Status {
    /** The command was executed, or was already true (commands are idempotent). */
    COMPLETED,
    /** The simulator refused it by rule. */
    REJECTED,
    /** The simulator tried and could not. */
    FAILED
  }

  /** The vehicle is not in this simulator's fleet (reason defined by the contract). */
  public static final String UNKNOWN_VEHICLE = "UNKNOWN_VEHICLE";

  /** A command received after its expiresAt (reason defined by the contract). */
  public static final String EXPIRED = "EXPIRED";

  /** A lock while the vehicle is between hubs: it can only be locked at a hub. */
  public static final String NOT_AT_HUB = "NOT_AT_HUB";

  /** An unlock of a vehicle with an active fault: it must not be ridden. */
  public static final String VEHICLE_FAULT = "VEHICLE_FAULT";

  private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]*");

  public CommandOutcome {
    Objects.requireNonNull(status, "status");
    if (status == Status.COMPLETED && reason != null) {
      throw new IllegalArgumentException("A completed command has no reason: " + reason);
    }
    if (status != Status.COMPLETED && (reason == null || !CODE.matcher(reason).matches())) {
      throw new IllegalArgumentException("Reason must be UPPER_SNAKE_CASE: " + reason);
    }
  }

  public static CommandOutcome completed() {
    return new CommandOutcome(Status.COMPLETED, null);
  }

  public static CommandOutcome rejected(String reason) {
    return new CommandOutcome(Status.REJECTED, reason);
  }
}
