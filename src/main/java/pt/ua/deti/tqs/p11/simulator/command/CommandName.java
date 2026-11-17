package pt.ua.deti.tqs.p11.simulator.command;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** Commands of contract version 1. The name is the last level of the command topic. */
public enum CommandName {
  LOCK,
  UNLOCK,
  PING;

  /** The name in topics and responses: {@code lock}, {@code unlock} or {@code ping}. */
  public String wireName() {
    return name().toLowerCase(Locale.ROOT);
  }

  /** The command with this exact wire name, or empty for an unknown (or wrongly cased) name. */
  public static Optional<CommandName> fromWireName(String wireName) {
    return Arrays.stream(values()).filter(c -> c.wireName().equals(wireName)).findFirst();
  }
}
