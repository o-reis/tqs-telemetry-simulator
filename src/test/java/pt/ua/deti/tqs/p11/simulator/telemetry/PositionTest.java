package pt.ua.deti.tqs.p11.simulator.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PositionTest {

  @ParameterizedTest
  @CsvSource({"-90, -180", "90, 180", "0, 0", "40.6405, -8.6538"})
  void of_coordinatesWithinRange_keepsThem(double lat, double lon) {
    Position position = new Position(lat, lon);

    assertThat(position.lat()).isEqualTo(lat);
    assertThat(position.lon()).isEqualTo(lon);
  }

  @ParameterizedTest
  @CsvSource({"-90.0001, 0", "90.0001, 0", "0, -180.0001", "0, 180.0001", "NaN, 0", "0, NaN"})
  void of_coordinatesOutsideRange_isRejected(double lat, double lon) {
    assertThatThrownBy(() -> new Position(lat, lon)).isInstanceOf(IllegalArgumentException.class);
  }
}
