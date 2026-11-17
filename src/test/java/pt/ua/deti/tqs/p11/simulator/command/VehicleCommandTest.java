package pt.ua.deti.tqs.p11.simulator.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class VehicleCommandTest {
  private static final UUID COMMAND_ID = UUID.fromString("e5b2a9c1-0d3f-4a8e-8b77-1c2d3e4f5a04");
  private static final Instant EXPIRES_AT = Instant.parse("2026-11-10T10:10:30Z");

  private static VehicleCommand unlockExpiringAt(Instant expiresAt) {
    return new VehicleCommand("fleet-01", "bike-0042", CommandName.UNLOCK, COMMAND_ID, expiresAt);
  }

  @Test
  void isExpiredAt_beforeTheDeadline_isFalse() {
    assertThat(unlockExpiringAt(EXPIRES_AT).isExpiredAt(EXPIRES_AT.minusMillis(1))).isFalse();
  }

  @Test
  void isExpiredAt_exactlyAtTheDeadline_isFalse() {
    assertThat(unlockExpiringAt(EXPIRES_AT).isExpiredAt(EXPIRES_AT)).isFalse();
  }

  @Test
  void isExpiredAt_oneMillisecondAfterTheDeadline_isTrue() {
    assertThat(unlockExpiringAt(EXPIRES_AT).isExpiredAt(EXPIRES_AT.plusMillis(1))).isTrue();
  }

  @Test
  void isExpiredAt_withoutADeadline_isNeverTrue() {
    assertThat(unlockExpiringAt(null).isExpiredAt(Instant.MAX)).isFalse();
  }

  @Test
  void constructor_missingRequiredValue_isRefused() {
    assertThatThrownBy(
            () -> new VehicleCommand("fleet-01", "bike-0042", null, COMMAND_ID, EXPIRES_AT))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () -> new VehicleCommand("fleet-01", "bike-0042", CommandName.LOCK, null, EXPIRES_AT))
        .isInstanceOf(NullPointerException.class);
  }

  @ParameterizedTest
  @EnumSource(CommandName.class)
  void commandName_roundTripsThroughItsWireName(CommandName name) {
    assertThat(CommandName.fromWireName(name.wireName())).contains(name);
  }

  @Test
  void commandName_wireNamesAreTheContractNames() {
    assertThat(CommandName.LOCK.wireName()).isEqualTo("lock");
    assertThat(CommandName.UNLOCK.wireName()).isEqualTo("unlock");
    assertThat(CommandName.PING.wireName()).isEqualTo("ping");
  }

  @Test
  void commandName_unknownOrWrongCase_isEmpty() {
    assertThat(CommandName.fromWireName("reboot")).isEmpty();
    assertThat(CommandName.fromWireName("LOCK")).isEmpty();
  }
}
