package pt.ua.deti.tqs.p11.simulator.contract;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ContractTopicsTest {
  @Test
  void telemetry_isPublishedOnTheVehicleTelemetryTopic() {
    assertThat(ContractTopics.telemetry("fleet-01", "bike-0042"))
        .isEqualTo("fleet/fleet-01/vehicles/bike-0042/telemetry");
  }

  @Test
  void events_areSentOnTheVehicleEventsTopic() {
    assertThat(ContractTopics.events("fleet-01", "bike-0042"))
        .isEqualTo("fleet/fleet-01/vehicles/bike-0042/events");
  }

  @Test
  void responses_areSentOnTheVehicleResponsesTopic() {
    assertThat(ContractTopics.responses("fleet-01", "bike-0042"))
        .isEqualTo("fleet/fleet-01/vehicles/bike-0042/responses");
  }

  @Test
  void commandsOfFleet_matchesEveryCommandOfEveryVehicleOfTheFleet() {
    assertThat(ContractTopics.commandsOfFleet("fleet-01"))
        .isEqualTo("fleet/fleet-01/vehicles/+/commands/+");
  }
}
