package pt.ua.deti.tqs.p11.simulator.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class FaultCatalogTest {

  @Test
  void everyCode_hasASeverity() {
    assertThat(FaultCatalog.CODES).isNotEmpty();
    assertThat(FaultCatalog.CODES)
        .allSatisfy(code -> assertThat(FaultCatalog.severityOf(code)).isNotNull());
  }

  @Test
  void batteryOverheat_isCritical_andTheOtherFaultsAreWarnings() {
    assertThat(FaultCatalog.severityOf("BATTERY_OVERHEAT")).isEqualTo(Severity.CRITICAL);
    assertThat(FaultCatalog.severityOf("BRAKE_WEAR")).isEqualTo(Severity.WARNING);
    assertThat(FaultCatalog.severityOf("FLAT_TIRE")).isEqualTo(Severity.WARNING);
  }

  @Test
  void isKnown_distinguishesTheCodesOfTheSimulatorFromOthers() {
    assertThat(FaultCatalog.isKnown("FLAT_TIRE")).isTrue();
    assertThat(FaultCatalog.isKnown("MOTOR_STALL")).isFalse();
    assertThat(FaultCatalog.isKnown("flat_tire")).isFalse();
  }

  @Test
  void severityOf_unknownCode_isRejected() {
    assertThatThrownBy(() -> FaultCatalog.severityOf("MOTOR_STALL"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("MOTOR_STALL");
  }
}
