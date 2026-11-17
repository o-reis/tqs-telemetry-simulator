package pt.ua.deti.tqs.p11.simulator.command;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A valid command from the platform to one vehicle.
 *
 * @param commandId chosen by the platform, echoed in the response
 * @param expiresAt after this instant the command must be rejected; {@code null} when the platform
 *     gave no deadline (the field is optional in the contract)
 */
public record VehicleCommand(
    String fleetId, String vehicleId, CommandName name, UUID commandId, Instant expiresAt) {

  public VehicleCommand {
    Objects.requireNonNull(fleetId, "fleetId");
    Objects.requireNonNull(vehicleId, "vehicleId");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(commandId, "commandId");
  }

  /**
   * Whether the command arrived too late. The contract rejects a command received after {@code
   * expiresAt}, so at exactly {@code expiresAt} it is still on time.
   */
  public boolean isExpiredAt(Instant now) {
    return expiresAt != null && now.isAfter(expiresAt);
  }
}
