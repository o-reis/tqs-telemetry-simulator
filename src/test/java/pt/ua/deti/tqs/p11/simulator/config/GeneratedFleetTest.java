package pt.ua.deti.tqs.p11.simulator.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import pt.ua.deti.tqs.p11.simulator.telemetry.Position;

/** A large fleet from a count: deterministic, spread over the hubs, every vehicle bookable. */
class GeneratedFleetTest {
  private static final List<HubConfig> THREE_HUBS =
      List.of(
          new HubConfig("hub-01", "Hub one", new Position(40.6443, -8.6455)),
          new HubConfig("hub-02", "Hub two", new Position(40.6412, -8.6501)),
          new HubConfig("hub-03", "Hub three", new Position(40.6405, -8.6538)));

  @Test
  void vehicles_hasExactlyTheCount() {
    assertThat(GeneratedFleet.vehicles(1000, "load", THREE_HUBS)).hasSize(1000);
  }

  @Test
  void vehicles_areNumberedFromOneWithTheGivenPrefix() {
    assertThat(GeneratedFleet.vehicles(3, "load", THREE_HUBS))
        .extracting(VehicleConfig::id)
        .containsExactly("load-0001", "load-0002", "load-0003");
  }

  @ParameterizedTest(name = "{0} vehicles: last id {1}")
  @CsvSource({"1, load-0001", "9999, load-9999", "10000, load-10000", "100000, load-100000"})
  void ids_haveAtLeastFourDigits_andMoreOnlyWhenTheCountNeedsThem(int count, String lastId) {
    List<VehicleConfig> fleet = GeneratedFleet.vehicles(count, "load", THREE_HUBS);

    assertThat(fleet.getLast().id()).isEqualTo(lastId);
    assertThat(fleet).extracting(v -> v.id().length()).containsOnly(lastId.length());
  }

  @Test
  void ids_areUnique() {
    assertThat(GeneratedFleet.vehicles(5000, "load", THREE_HUBS))
        .extracting(VehicleConfig::id)
        .doesNotHaveDuplicates();
  }

  @Test
  void hubs_areUsedInTurn() {
    assertThat(GeneratedFleet.vehicles(7, "load", THREE_HUBS))
        .extracting(VehicleConfig::hubId)
        .containsExactly("hub-01", "hub-02", "hub-03", "hub-01", "hub-02", "hub-03", "hub-01");
  }

  @Test
  void types_alternate_startingWithAnEBike() {
    assertThat(GeneratedFleet.vehicles(4, "load", THREE_HUBS))
        .extracting(VehicleConfig::type)
        .containsExactly(
            VehicleType.E_BIKE, VehicleType.E_SCOOTER, VehicleType.E_BIKE, VehicleType.E_SCOOTER);
  }

  /** BR1: a vehicle can be booked from 20 %, so a load test fleet is fully bookable. */
  @Test
  void batteries_stayBetween20And100() {
    assertThat(GeneratedFleet.vehicles(5000, "load", THREE_HUBS))
        .extracting(VehicleConfig::battery)
        .allSatisfy(b -> assertThat(b).isBetween(20.0, 100.0));
  }

  @Test
  void batteries_with81VehiclesOrMore_takeEveryWholeValueFrom20To100() {
    List<Double> every = IntStream.rangeClosed(20, 100).mapToObj(b -> (double) b).toList();

    assertThat(GeneratedFleet.vehicles(81, "load", THREE_HUBS))
        .extracting(VehicleConfig::battery)
        .containsExactlyInAnyOrderElementsOf(every);
  }

  @Test
  void vehicles_sameInput_giveTheSameFleet() {
    assertThat(GeneratedFleet.vehicles(500, "load", THREE_HUBS))
        .isEqualTo(GeneratedFleet.vehicles(500, "load", THREE_HUBS));
  }

  @ParameterizedTest(name = "{0} vehicles: prefix up to {1} characters")
  @CsvSource({"1, 59", "9999, 59", "10000, 58", "100000, 57"})
  void maxPrefixLength_keepsEveryIdWithinTheContractsSixtyFourCharacters(int count, int max) {
    assertThat(GeneratedFleet.maxPrefixLength(count)).isEqualTo(max);
    assertThat(GeneratedFleet.vehicles(count, "p".repeat(max), THREE_HUBS).getLast().id())
        .hasSize(64);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1, 100_001})
  void vehicles_countOutsideOneTo100000_isRefused(int count) {
    assertThatThrownBy(() -> GeneratedFleet.vehicles(count, "load", THREE_HUBS))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void vehicles_withoutHubs_isRefused() {
    assertThatThrownBy(() -> GeneratedFleet.vehicles(3, "load", List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
