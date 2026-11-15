package pt.ua.deti.tqs.p11.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest
class SimulatorApplicationIT {

  @Autowired private ApplicationContext context;

  @Test
  void applicationContext_starts_withTheApplicationClass() {
    assertThat(context.getBeansOfType(SimulatorApplication.class)).hasSize(1);
  }
}
