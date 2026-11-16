package pt.ua.deti.tqs.p11.simulator.engine;

import java.util.List;
import java.util.random.RandomGenerator;
import pt.ua.deti.tqs.p11.simulator.config.ScenarioConfig;
import pt.ua.deti.tqs.p11.simulator.telemetry.TelemetryPublisher;

public class SimulationEngine {

  static final List<String> FAULT_CODES = List.of("BRAKE_WEAR", "FLAT_TIRE", "BATTERY_OVERHEAT");

  public SimulationEngine(
      ScenarioConfig scenario, TelemetryPublisher publisher, RandomGenerator random) {}

  public void tick() {}
}
