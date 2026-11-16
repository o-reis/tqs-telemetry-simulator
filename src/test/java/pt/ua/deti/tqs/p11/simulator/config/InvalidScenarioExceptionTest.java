package pt.ua.deti.tqs.p11.simulator.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class InvalidScenarioExceptionTest {

  @Test
  void message_withOneProblem_usesTheSingularAndListsIt() {
    InvalidScenarioException exception =
        new InvalidScenarioException(List.of("vehicles[0].battery: bad"));

    assertThat(exception).hasMessage("Invalid scenario (1 problem):\n - vehicles[0].battery: bad");
  }

  @Test
  void message_withSeveralProblems_listsEachOnItsOwnLine() {
    InvalidScenarioException exception =
        new InvalidScenarioException(List.of("a: first", "b: second"));

    assertThat(exception).hasMessage("Invalid scenario (2 problems):\n - a: first\n - b: second");
  }

  @Test
  void getProblems_returnsTheProblemsInOrder() {
    InvalidScenarioException exception = new InvalidScenarioException(List.of("a: x", "b: y"));

    assertThat(exception.getProblems()).containsExactly("a: x", "b: y");
  }

  @Test
  void message_withOrigin_namesWhereTheScenarioCameFrom() {
    InvalidScenarioException exception =
        new InvalidScenarioException("scenarios/x.yml", List.of("a: first"));

    assertThat(exception)
        .hasMessage("Invalid scenario in scenarios/x.yml (1 problem):\n - a: first");
  }
}
