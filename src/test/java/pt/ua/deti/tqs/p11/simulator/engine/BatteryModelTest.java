package pt.ua.deti.tqs.p11.simulator.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import pt.ua.deti.tqs.p11.simulator.engine.BatteryModel.Activity;

/**
 * The model is pure: no Spring, no real time, no randomness. Drain and charge use different rates
 * in most tests, so swapping them is caught.
 */
class BatteryModelTest {

  private static final double EPSILON = 1e-9;

  private final BatteryModel model = new BatteryModel(0.5, 1.0);

  // ---- The three situations ----

  @Test
  void next_riding_drainsTheRateTimesTheElapsedMinutes() {
    assertThat(model.next(50, Activity.RIDING, 60)).isEqualTo(49.5);
  }

  @Test
  void next_parkedAtHub_chargesTheRateTimesTheElapsedMinutes() {
    assertThat(model.next(50, Activity.PARKED_AT_HUB, 60)).isEqualTo(51.0);
  }

  @Test
  void next_otherActivity_leavesTheBatteryUnchanged() {
    assertThat(model.next(50, Activity.OTHER, 60)).isEqualTo(50.0);
  }

  @Test
  void next_ridingWithATenSecondTick_drainsAFractionOfAMinute() {
    assertThat(model.next(50, Activity.RIDING, 10)).isCloseTo(50 - 0.5 / 6, within(EPSILON));
  }

  @Test
  void next_subSecondInterval_keepsThePrecision() {
    assertThat(model.next(50, Activity.RIDING, 0.5))
        .isCloseTo(50 - 0.5 * 0.5 / 60, within(EPSILON));
    assertThat(model.next(50, Activity.PARKED_AT_HUB, 0.5))
        .isCloseTo(50 + 1.0 * 0.5 / 60, within(EPSILON));
  }

  @Test
  void next_zeroElapsedTime_changesNothing() {
    assertThat(model.next(50, Activity.RIDING, 0)).isEqualTo(50.0);
    assertThat(model.next(50, Activity.PARKED_AT_HUB, 0)).isEqualTo(50.0);
  }

  // ---- Limits: the battery stays between 0 and 100 ----

  @ParameterizedTest(name = "{0} from {1} % for {2} s ends at {3} %")
  @CsvSource({
    "RIDING, 0, 60, 0",
    "RIDING, 0.2, 60, 0",
    "RIDING, 0.5, 60, 0",
    "PARKED_AT_HUB, 100, 60, 100",
    "PARKED_AT_HUB, 99.5, 60, 100",
    "PARKED_AT_HUB, 99, 60, 100"
  })
  void next_whenTheRateReachesOrPassesALimit_stopsAtTheLimit(
      Activity activity, double battery, double seconds, double expected) {
    assertThat(model.next(battery, activity, seconds)).isEqualTo(expected);
  }

  @Test
  void next_justInsideTheLimits_isNotClamped() {
    assertThat(model.next(0.6, Activity.RIDING, 60)).isCloseTo(0.1, within(EPSILON));
    assertThat(model.next(98.9, Activity.PARKED_AT_HUB, 60)).isCloseTo(99.9, within(EPSILON));
  }

  // ---- Each rate only applies to its own situation ----

  @ParameterizedTest
  @EnumSource(Activity.class)
  void next_withBothRatesZero_neverChangesTheBattery(Activity activity) {
    assertThat(new BatteryModel(0, 0).next(42.5, activity, 60)).isEqualTo(42.5);
  }

  @Test
  void next_withZeroChargeRate_parkedVehicleKeepsItsBattery() {
    assertThat(new BatteryModel(2.0, 0).next(50, Activity.PARKED_AT_HUB, 60)).isEqualTo(50.0);
  }

  @Test
  void next_withZeroDrainRate_ridingVehicleKeepsItsBattery() {
    assertThat(new BatteryModel(0, 2.0).next(50, Activity.RIDING, 60)).isEqualTo(50.0);
  }

  // ---- Invalid input ----

  @ParameterizedTest
  @ValueSource(doubles = {-0.1, Double.NaN, Double.POSITIVE_INFINITY})
  void constructor_invalidDrainRate_isRejected(double rate) {
    assertThatThrownBy(() -> new BatteryModel(rate, 1.0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(doubles = {-0.1, Double.NaN, Double.POSITIVE_INFINITY})
  void constructor_invalidChargeRate_isRejected(double rate) {
    assertThatThrownBy(() -> new BatteryModel(0.5, rate))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(doubles = {-0.1, 100.1, Double.NaN})
  void next_batteryOutsideTheRange_isRejected(double battery) {
    assertThatThrownBy(() -> model.next(battery, Activity.RIDING, 60))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(doubles = {-1, Double.NaN})
  void next_invalidElapsedTime_isRejected(double seconds) {
    assertThatThrownBy(() -> model.next(50, Activity.RIDING, seconds))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
