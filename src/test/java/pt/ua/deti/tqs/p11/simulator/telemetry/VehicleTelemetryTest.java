package pt.ua.deti.tqs.p11.simulator.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class VehicleTelemetryTest {

  private static final Position AVEIRO = new Position(40.6405, -8.6538);

  private static VehicleTelemetry withBattery(double batteryPercent) {
    return new VehicleTelemetry(
        "fleet-01", "bike-0042", batteryPercent, LockState.LOCKED, AVEIRO, "hub-03", null, null);
  }

  @ParameterizedTest
  @ValueSource(doubles = {0, 0.1, 99.9, 100})
  void batteryWithinRange_isAccepted(double batteryPercent) {
    assertThat(withBattery(batteryPercent).batteryPercent()).isEqualTo(batteryPercent);
  }

  @ParameterizedTest
  @ValueSource(doubles = {-0.1, 100.1, Double.NaN})
  void batteryOutsideRange_isRejected(double batteryPercent) {
    assertThatThrownBy(() -> withBattery(batteryPercent))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void noPositionAndNoHub_isRejected() {
    assertThatThrownBy(() ->
            new VehicleTelemetry(
                "fleet-01", "bike-0042", 50, LockState.LOCKED, null, null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void hubOnly_isAccepted() {
    VehicleTelemetry telemetry =
        new VehicleTelemetry(
            "fleet-01", "bike-0042", 50, LockState.LOCKED, null, "hub-03", null, null);

    assertThat(telemetry.position()).isEmpty();
    assertThat(telemetry.hubId()).contains("hub-03");
  }

  @Test
  void positionOnly_isAccepted() {
    VehicleTelemetry telemetry =
        new VehicleTelemetry(
            "fleet-01", "bike-0042", 50, LockState.UNLOCKED, AVEIRO, null, null, 12.5);

    assertThat(telemetry.position()).contains(AVEIRO);
    assertThat(telemetry.hubId()).isEmpty();
    assertThat(telemetry.speedKmh()).contains(12.5);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "bike/0042", "bike+0042", "bike#0042", "bike 0042"})
  void vehicleIdUnsafeInATopic_isRejected(String vehicleId) {
    assertThatThrownBy(() ->
            new VehicleTelemetry(
                "fleet-01", vehicleId, 50, LockState.LOCKED, AVEIRO, null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void identifierOf64Characters_isAccepted_andOf65_isRejected() {
    String id64 = "a".repeat(64);
    String id65 = "a".repeat(65);

    assertThat(
            new VehicleTelemetry(id64, "bike-0042", 50, LockState.LOCKED, AVEIRO, null, null, null)
                .fleetId())
        .isEqualTo(id64);
    assertThatThrownBy(() ->
            new VehicleTelemetry(id65, "bike-0042", 50, LockState.LOCKED, AVEIRO, null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void hubIdUnsafeInATopic_isRejected() {
    assertThatThrownBy(() ->
            new VehicleTelemetry(
                "fleet-01", "bike-0042", 50, LockState.LOCKED, null, "hub/03", null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"brake_wear", "1BRAKE", "BRAKE-WEAR", ""})
  void faultCodeNotInUpperSnakeCase_isRejected(String faultCode) {
    assertThatThrownBy(() ->
            new VehicleTelemetry(
                "fleet-01", "bike-0042", 50, LockState.LOCKED, AVEIRO, null, faultCode, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void faultCodeInUpperSnakeCase_isAccepted() {
    VehicleTelemetry telemetry =
        new VehicleTelemetry(
            "fleet-01", "bike-0042", 50, LockState.LOCKED, AVEIRO, null, "BRAKE_WEAR", null);

    assertThat(telemetry.faultCode()).contains("BRAKE_WEAR");
  }

  @Test
  void negativeSpeed_isRejected_andZero_isAccepted() {
    assertThat(
            new VehicleTelemetry(
                    "fleet-01", "bike-0042", 50, LockState.LOCKED, AVEIRO, null, null, 0.0)
                .speedKmh())
        .contains(0.0);
    assertThatThrownBy(() ->
            new VehicleTelemetry(
                "fleet-01", "bike-0042", 50, LockState.LOCKED, AVEIRO, null, null, -0.1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void missingLockState_isRejected() {
    assertThatThrownBy(
            () -> new VehicleTelemetry("fleet-01", "bike-0042", 50, null, AVEIRO, null, null, null))
        .isInstanceOf(NullPointerException.class);
  }
}
