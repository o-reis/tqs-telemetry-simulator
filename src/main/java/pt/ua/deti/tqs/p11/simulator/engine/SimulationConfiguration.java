package pt.ua.deti.tqs.p11.simulator.engine;

import java.time.Clock;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.random.RandomGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pt.ua.deti.tqs.p11.simulator.command.CommandService;
import pt.ua.deti.tqs.p11.simulator.command.ResponsePublisher;
import pt.ua.deti.tqs.p11.simulator.config.ScenarioConfig;
import pt.ua.deti.tqs.p11.simulator.event.EventPublisher;
import pt.ua.deti.tqs.p11.simulator.telemetry.TelemetryPublisher;

/**
 * Wires the simulation. The run has exactly one clock and one random generator, seeded from the
 * scenario; every component that must be reproducible receives these two beans.
 */
@Configuration(proxyBeanMethods = false)
public class SimulationConfiguration {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  /**
   * java.util.Random, because its algorithm is fixed by its specification: the same seed gives the
   * same numbers on every JDK.
   */
  @Bean
  RandomGenerator simulationRandom(ScenarioConfig scenario) {
    return new Random(scenario.simulation().seed());
  }

  @Bean
  SimulationEngine simulationEngine(
      ScenarioConfig scenario,
      TelemetryPublisher publisher,
      EventPublisher eventPublisher,
      RandomGenerator simulationRandom) {
    return new SimulationEngine(scenario, publisher, eventPublisher, simulationRandom);
  }

  /** One thread, so ticks never overlap and the order of random draws is always the same. */
  // shutdown, not shutdownNow: a tick in progress finishes instead of being interrupted.
  @Bean(destroyMethod = "shutdown")
  ScheduledExecutorService simulationScheduler() {
    return Executors.newSingleThreadScheduledExecutor(
        runnable -> Thread.ofPlatform().name("simulation").daemon().unstarted(runnable));
  }

  @Bean
  SimulationRunner simulationRunner(
      SimulationEngine engine, ScenarioConfig scenario, ScheduledExecutorService scheduler) {
    return new SimulationRunner(
        engine::tick,
        SimulationRunner.intervalOf(scenario.simulation().emissionIntervalSeconds()),
        scheduler);
  }

  /**
   * Gets the same executor as the ticks: commands and ticks then run on one thread, one after
   * another, so the engine (not thread-safe) is never used by two threads at once.
   */
  @Bean
  CommandService commandService(
      SimulationEngine engine,
      ResponsePublisher responsePublisher,
      Clock clock,
      ScheduledExecutorService simulationScheduler) {
    return new CommandService(engine, responsePublisher, clock, simulationScheduler);
  }
}
