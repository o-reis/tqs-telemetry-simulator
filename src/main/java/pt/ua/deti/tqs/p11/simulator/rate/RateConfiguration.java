package pt.ua.deti.tqs.p11.simulator.rate;

import java.time.Clock;
import java.util.concurrent.ScheduledExecutorService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pt.ua.deti.tqs.p11.simulator.config.ScenarioConfig;

/** Wires the emission rate meter and its report in the log. */
@Configuration(proxyBeanMethods = false)
public class RateConfiguration {
  @Bean
  EmissionRateMeter emissionRateMeter(Clock clock, ScenarioConfig scenario) {
    return EmissionRateMeter.forScenario(clock, scenario);
  }

  /** On the simulation's executor: the reports add no thread. */
  @Bean
  RateReporter rateReporter(
      EmissionRateMeter emissionRateMeter, ScheduledExecutorService simulationScheduler) {
    return new RateReporter(emissionRateMeter, RateReporter.DEFAULT_PERIOD, simulationScheduler);
  }
}
