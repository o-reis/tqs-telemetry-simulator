package pt.ua.deti.tqs.p11.simulator.contract;

/**
 * A message on a command topic that is not a valid command of the contract. No response can be sent
 * for it, so the adapter logs the message and drops it.
 */
public class InvalidCommandMessageException extends RuntimeException {
  public InvalidCommandMessageException(String message) {
    super(message);
  }
}
