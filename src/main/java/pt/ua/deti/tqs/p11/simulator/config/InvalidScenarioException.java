package pt.ua.deti.tqs.p11.simulator.config;

import java.util.List;

/** The scenario file breaks one or more rules. Lists every problem, not only the first. */
public class InvalidScenarioException extends RuntimeException {

  // Not serialized: an exception of this kind is never sent between JVMs.
  private final transient List<String> problems;

  public InvalidScenarioException(List<String> problems) {
    super("Invalid scenario");
    this.problems = List.copyOf(problems);
  }

  public List<String> getProblems() {
    return problems;
  }
}
