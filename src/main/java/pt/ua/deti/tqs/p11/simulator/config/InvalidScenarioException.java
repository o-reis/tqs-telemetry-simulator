package pt.ua.deti.tqs.p11.simulator.config;

import java.util.List;
import java.util.stream.Collectors;

/** The scenario file breaks one or more rules. Lists every problem, not only the first. */
public class InvalidScenarioException extends RuntimeException {

  // Not serialized: an exception of this kind is never sent between JVMs.
  private final transient List<String> problems;

  public InvalidScenarioException(List<String> problems) {
    this(null, problems);
  }

  /**
   * @param origin where the scenario came from (a file name, say), or null if not known
   */
  public InvalidScenarioException(String origin, List<String> problems) {
    super(message(origin, problems));
    this.problems = List.copyOf(problems);
  }

  public List<String> getProblems() {
    return problems;
  }

  private static String message(String origin, List<String> problems) {
    String where = origin == null ? "" : " in " + origin;
    String count = problems.size() + (problems.size() == 1 ? " problem" : " problems");
    String header = "Invalid scenario" + where + " (" + count + "):";
    return problems.stream().map(p -> "\n - " + p).collect(Collectors.joining("", header, ""));
  }
}
