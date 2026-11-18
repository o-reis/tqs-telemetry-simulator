package pt.ua.deti.tqs.p11.simulator.event;

/** The moments of a fault that the contract reports as events (contract/README.md, Event). */
public enum FaultEventType {
  /** A fault appeared. */
  FAULT_RAISED,
  /** A fault went away. */
  FAULT_CLEARED
}
