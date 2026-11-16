package pt.ua.deti.tqs.p11.simulator.contract;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ContractTopicsTest {

  @Test
  void telemetry_isPublishedOnTheVehicleTelemetryTopic() {
    assertThat(ContractTopics.telemetry("fleet-01", "bike-0042"))
        .isEqualTo("fleet/fleet-01/vehicles/bike-0042/telemetry");
  }
}
