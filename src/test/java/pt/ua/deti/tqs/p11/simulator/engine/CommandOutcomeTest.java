package pt.ua.deti.tqs.p11.simulator.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import pt.ua.deti.tqs.p11.simulator.engine.CommandOutcome.Status;

/** The outcome mirrors the contract's response: a reason exactly when not completed. */
class CommandOutcomeTest {
  @Test
  void completed_hasNoReason() {
    CommandOutcome outcome = CommandOutcome.completed();

    assertThat(outcome.status()).isEqualTo(Status.COMPLETED);
    assertThat(outcome.reason()).isNull();
  }

  @Test
  void rejected_keepsItsReason() {
    CommandOutcome outcome = CommandOutcome.rejected("NOT_AT_HUB");

    assertThat(outcome.status()).isEqualTo(Status.REJECTED);
    assertThat(outcome.reason()).isEqualTo("NOT_AT_HUB");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"not_at_hub", "Not_At_Hub", "NOT AT HUB", "1_NOT_AT_HUB"})
  void rejected_reasonNotInUpperSnakeCase_isRefused(String reason) {
    assertThatThrownBy(() -> CommandOutcome.rejected(reason))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void completedWithAReason_isRefused() {
    assertThatThrownBy(() -> new CommandOutcome(Status.COMPLETED, "NOT_AT_HUB"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void missingStatus_isRefused() {
    assertThatThrownBy(() -> new CommandOutcome(null, null))
        .isInstanceOf(NullPointerException.class);
  }
}
