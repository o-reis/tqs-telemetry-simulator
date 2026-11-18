package pt.ua.deti.tqs.p11.simulator.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class FaultEventTest {

  @Test
  void faultCode_inUpperSnakeCase_isAccepted() {
    FaultEvent event =
        new FaultEvent(
            "fleet-01", "bike-0001", FaultEventType.FAULT_RAISED, "FLAT_TIRE", Severity.WARNING);

    assertThat(event.faultCode()).isEqualTo("FLAT_TIRE");
  }

  @Test
  void faultCode_inLowerCase_isRejected() {
    assertThatThrownBy(() ->
            new FaultEvent(
                "fleet-01",
                "bike-0001",
                FaultEventType.FAULT_RAISED,
                "flat_tire",
                Severity.WARNING))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("UPPER_SNAKE_CASE");
  }

  @Test
  void faultCode_missing_isRejected() {
    assertThatThrownBy(() ->
            new FaultEvent(
                "fleet-01", "bike-0001", FaultEventType.FAULT_CLEARED, null, Severity.WARNING))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
