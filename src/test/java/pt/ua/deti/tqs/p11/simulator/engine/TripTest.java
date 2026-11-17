package pt.ua.deti.tqs.p11.simulator.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pt.ua.deti.tqs.p11.simulator.telemetry.Position;

/**
 * A trip is pure geometry: where a vehicle is after some seconds of riding from one hub to another.
 * No Spring, no real time, no randomness.
 */
class TripTest {
  private static final double EPSILON = 1e-9;

  /** Estação de Aveiro and Universidade: they differ in both latitude and longitude. */
  private static final Position ORIGIN = new Position(40.6443, -8.6455);

  private static final Position DESTINATION = new Position(40.6304, -8.6577);

  private static final double DURATION_SECONDS = 120;

  // ---- Distance between two points on the Earth (Haversine, radius 6371 km) ----
  /** One degree along a meridian is pi * 6371 / 180 km. */
  private static final double KM_PER_DEGREE_OF_LATITUDE = Math.PI * 6371 / 180;

  private static final double DISTANCE_EPSILON_KM = 0.001;

  private final Trip trip = new Trip(ORIGIN, DESTINATION, DURATION_SECONDS);

  @Test
  void positionAt_start_isTheOriginHub() {
    assertThat(trip.positionAt(0)).isEqualTo(ORIGIN);
  }

  @Test
  void positionAt_end_isExactlyTheDestinationHub() {
    assertThat(trip.positionAt(DURATION_SECONDS)).isEqualTo(DESTINATION);
  }

  @Test
  void positionAt_afterTheEnd_staysAtTheDestinationHub() {
    assertThat(trip.positionAt(DURATION_SECONDS + 500)).isEqualTo(DESTINATION);
  }

  @Test
  void positionAt_halfway_isTheMidpointBetweenTheHubs() {
    Position halfway = trip.positionAt(DURATION_SECONDS / 2);

    assertThat(halfway.lat()).isCloseTo((ORIGIN.lat() + DESTINATION.lat()) / 2, within(EPSILON));
    assertThat(halfway.lon()).isCloseTo((ORIGIN.lon() + DESTINATION.lon()) / 2, within(EPSILON));
  }

  @Test
  void positionAt_aQuarterOfTheWay_hasCoveredAQuarterOfTheDistance() {
    Position quarter = trip.positionAt(DURATION_SECONDS / 4);

    assertThat(quarter.lat())
        .isCloseTo(ORIGIN.lat() + (DESTINATION.lat() - ORIGIN.lat()) / 4, within(EPSILON));
    assertThat(quarter.lon())
        .isCloseTo(ORIGIN.lon() + (DESTINATION.lon() - ORIGIN.lon()) / 4, within(EPSILON));
  }

  @Test
  void positionAt_everyTenSeconds_getsCloserToTheDestination() {
    List<Double> distances = new ArrayList<>();
    for (double s = 0; s <= DURATION_SECONDS; s += 10) {
      distances.add(degreesBetween(trip.positionAt(s), DESTINATION));
    }

    assertThat(distances).hasSize(13).isSortedAccordingTo((a, b) -> Double.compare(b, a));
    assertThat(distances).doesNotHaveDuplicates();
  }

  @ParameterizedTest(name = "after {0} s")
  @ValueSource(doubles = {0, 1, 30, 59.5, 60, 90, 119.9, 120, 1000})
  void positionAt_neverLeavesTheBoxAroundTheTwoHubs(double seconds) {
    Position p = trip.positionAt(seconds);

    assertThat(p.lat())
        .isBetween(
            Math.min(ORIGIN.lat(), DESTINATION.lat()), Math.max(ORIGIN.lat(), DESTINATION.lat()));
    assertThat(p.lon())
        .isBetween(
            Math.min(ORIGIN.lon(), DESTINATION.lon()), Math.max(ORIGIN.lon(), DESTINATION.lon()));
  }

  @Test
  void isFinished_beforeTheDuration_isFalse() {
    assertThat(trip.isFinished(0)).isFalse();
    assertThat(trip.isFinished(DURATION_SECONDS - 0.1)).isFalse();
  }

  @Test
  void isFinished_atOrAfterTheDuration_isTrue() {
    assertThat(trip.isFinished(DURATION_SECONDS)).isTrue();
    assertThat(trip.isFinished(DURATION_SECONDS + 1)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(doubles = {0, -1, Double.NaN, Double.POSITIVE_INFINITY})
  void constructor_durationNotPositiveAndFinite_isRejected(double duration) {
    assertThatThrownBy(() -> new Trip(ORIGIN, DESTINATION, duration))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void constructor_missingHubPosition_isRejected() {
    assertThatThrownBy(() -> new Trip(null, DESTINATION, DURATION_SECONDS))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new Trip(ORIGIN, null, DURATION_SECONDS))
        .isInstanceOf(NullPointerException.class);
  }

  @ParameterizedTest
  @ValueSource(doubles = {-1, Double.NaN})
  void positionAt_invalidElapsedTime_isRejected(double seconds) {
    assertThatThrownBy(() -> trip.positionAt(seconds)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void distanceKm_oneDegreeOfLatitude_isAbout111Km() {
    assertThat(Trip.distanceKm(new Position(0, 0), new Position(1, 0)))
        .isCloseTo(KM_PER_DEGREE_OF_LATITUDE, within(DISTANCE_EPSILON_KM));
  }

  @Test
  void distanceKm_betweenTwoAveiroHubs_isAbout1Point86Km() {
    assertThat(Trip.distanceKm(ORIGIN, DESTINATION)).isCloseTo(1.857, within(DISTANCE_EPSILON_KM));
  }

  @Test
  void distanceKm_isTheSameInBothDirections() {
    assertThat(Trip.distanceKm(DESTINATION, ORIGIN))
        .isEqualTo(Trip.distanceKm(ORIGIN, DESTINATION));
  }

  @Test
  void distanceKm_samePoint_isZero() {
    assertThat(Trip.distanceKm(ORIGIN, ORIGIN)).isZero();
  }

  @Test
  void between_takesTheDistanceDividedByTheSpeed() {
    // At a speed equal to the distance per hour, the trip takes exactly one hour.
    Trip oneHour = Trip.between(new Position(0, 0), new Position(1, 0), KM_PER_DEGREE_OF_LATITUDE);

    assertThat(oneHour.durationSeconds()).isCloseTo(3600, within(EPSILON));
  }

  @Test
  void between_twiceTheSpeed_takesHalfTheTime() {
    Trip slow = Trip.between(ORIGIN, DESTINATION, 10);
    Trip fast = Trip.between(ORIGIN, DESTINATION, 20);

    assertThat(fast.durationSeconds()).isCloseTo(slow.durationSeconds() / 2, within(EPSILON));
  }

  @Test
  void between_keepsTheHubPositions() {
    Trip planned = Trip.between(ORIGIN, DESTINATION, 15);

    assertThat(planned.origin()).isEqualTo(ORIGIN);
    assertThat(planned.destination()).isEqualTo(DESTINATION);
  }

  @ParameterizedTest
  @ValueSource(doubles = {0, -5, Double.NaN, Double.POSITIVE_INFINITY})
  void between_speedNotPositiveAndFinite_isRejected(double speedKmh) {
    assertThatThrownBy(() -> Trip.between(ORIGIN, DESTINATION, speedKmh))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void between_sameOriginAndDestination_isRejected() {
    assertThatThrownBy(() -> Trip.between(ORIGIN, ORIGIN, 15))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static double degreesBetween(Position a, Position b) {
    return Math.hypot(a.lat() - b.lat(), a.lon() - b.lon());
  }
}
