package pt.ua.deti.tqs.p11.simulator.engine;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import org.springframework.context.SmartLifecycle;

public class SimulationRunner implements SmartLifecycle {

  public SimulationRunner(Runnable tick, Duration interval, ScheduledExecutorService scheduler) {}

  static Duration intervalOf(double seconds) {
    return Duration.ZERO;
  }

  @Override
  public void start() {}

  @Override
  public void stop() {}

  @Override
  public boolean isRunning() {
    return false;
  }
}
