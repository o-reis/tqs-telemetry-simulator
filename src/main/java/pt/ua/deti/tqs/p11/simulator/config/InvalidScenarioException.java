package pt.ua.deti.tqs.p11.simulator.config;

import java.util.List;
import java.util.stream.Collectors;

/** The scenario file breaks one or more rules. Lists every problem, not only the first. */
public class InvalidScenarioException extends RuntimeException {

  // Not serialized: an exception of this kind is never sent between JVMs.
  private final transient List<String> problems;

  public InvalidScenarioException(List<String> problems) {
    super(message(problems));
    this.problems = List.copyOf(problems);
  }

  /**
   * @param origin where the scenario came from, for the message (not used yet)
   */
  public InvalidScenarioException(String origin, List<String> problems) {
    this(problems);
  }

  public List<String> getProblems() {
    return problems;
  }

  private static String message(List<String> problems) {
    String header =
        "Invalid scenario ("
            + problems.size()
            + (problems.size() == 1 ? " problem" : " problems")
            + "):";
    return problems.stream().map(p -> "\n - " + p).collect(Collectors.joining("", header, ""));
  }
}
