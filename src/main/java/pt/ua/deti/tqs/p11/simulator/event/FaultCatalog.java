package pt.ua.deti.tqs.p11.simulator.event;

import java.util.List;
import java.util.Map;

/**
 * The faults this simulator can raise. The contract does not list fault codes: each simulator
 * defines its own (contract/README.md, Event), so this is the list to document for the platform.
 */
public final class FaultCatalog {

  /** Codes in a fixed order, because the engine picks one by index from its seeded generator. */
  public static final List<String> CODES = List.of("BRAKE_WEAR", "FLAT_TIRE", "BATTERY_OVERHEAT");

  /** An overheating battery is the only fault that cannot wait. */
  private static final Map<String, Severity> SEVERITIES =
      Map.of(
          "BRAKE_WEAR", Severity.WARNING,
          "FLAT_TIRE", Severity.WARNING,
          "BATTERY_OVERHEAT", Severity.CRITICAL);

  private FaultCatalog() {}

  public static boolean isKnown(String code) {
    return CODES.contains(code);
  }

  /**
   * @throws IllegalArgumentException if the code is not one of {@link #CODES}
   */
  public static Severity severityOf(String code) {
    Severity severity = SEVERITIES.get(code);
    if (severity == null) {
      throw new IllegalArgumentException("Unknown fault code: " + code);
    }
    return severity;
  }
}
