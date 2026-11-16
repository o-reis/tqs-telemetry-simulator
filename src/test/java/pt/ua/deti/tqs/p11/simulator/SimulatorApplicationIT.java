package pt.ua.deti.tqs.p11.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import pt.ua.deti.tqs.p11.simulator.config.ScenarioConfig;

@SpringBootTest
class SimulatorApplicationIT {

  @Autowired private ApplicationContext context;
  @Autowired private ScenarioConfig scenario;

  @Test
  void applicationContext_starts_withTheApplicationClass() {
    assertThat(context.getBeansOfType(SimulatorApplication.class)).hasSize(1);
  }

  @Test
  void applicationContext_withoutConfiguration_runsTheRiaRideScenarioFromTheClasspath() {
    assertThat(scenario.fleetId()).isEqualTo("fleet-01");
    assertThat(scenario.hubs()).hasSizeGreaterThanOrEqualTo(2);
    assertThat(scenario.vehicles()).hasSizeGreaterThanOrEqualTo(20);
  }
}
