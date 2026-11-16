package pt.ua.deti.tqs.p11.simulator.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * The scheduler is the boundary to real time, so it is mocked: the test captures the task the
 * runner schedules and runs it by hand. No test waits for real time.
 */
class SimulationRunnerTest {

  private static final Duration INTERVAL = Duration.ofSeconds(10);

  private final ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
  private final ScheduledFuture<?> scheduled = mock(ScheduledFuture.class);
  private final AtomicInteger ticks = new AtomicInteger();

  @BeforeEach
  void schedulerReturnsAFuture() {
    doReturn(scheduled).when(scheduler).scheduleAtFixedRate(any(), anyLong(), anyLong(), any());
  }

  private Runnable scheduledTask() {
    ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
    verify(scheduler).scheduleAtFixedRate(task.capture(), anyLong(), anyLong(), any());
    return task.getValue();
  }

  @Test
  void start_schedulesTheTickAtTheConfiguredRate_startingNow() {
    new SimulationRunner(ticks::incrementAndGet, Duration.ofMillis(2500), scheduler).start();

    verify(scheduler)
        .scheduleAtFixedRate(any(), eq(0L), eq(2_500_000_000L), eq(TimeUnit.NANOSECONDS));
  }

  @Test
  void scheduledTask_runsOneTickEachTime() {
    new SimulationRunner(ticks::incrementAndGet, INTERVAL, scheduler).start();
    Runnable task = scheduledTask();

    task.run();
    task.run();

    assertThat(ticks).hasValue(2);
  }

  /** The executor never runs a task again once it throws, so one bad tick would end the run. */
  @Test
  void failingTick_doesNotEscapeTheScheduledTask() {
    Runnable failing =
        () -> {
          ticks.incrementAndGet();
          throw new IllegalStateException("boom");
        };
    new SimulationRunner(failing, INTERVAL, scheduler).start();
    Runnable task = scheduledTask();

    assertThatCode(task::run).doesNotThrowAnyException();
    assertThatCode(task::run).doesNotThrowAnyException();
    assertThat(ticks).hasValue(2);
  }

  @Test
  void isRunning_followsStartAndStop() {
    SimulationRunner runner = new SimulationRunner(ticks::incrementAndGet, INTERVAL, scheduler);

    assertThat(runner.isRunning()).isFalse();
    runner.start();
    assertThat(runner.isRunning()).isTrue();
    runner.stop();
    assertThat(runner.isRunning()).isFalse();
  }

  @Test
  void stop_cancelsTheScheduledTask_withoutInterruptingARunningTick() {
    SimulationRunner runner = new SimulationRunner(ticks::incrementAndGet, INTERVAL, scheduler);
    runner.start();

    runner.stop();

    verify(scheduled).cancel(false);
  }

  @Test
  void startTwice_schedulesOnce() {
    SimulationRunner runner = new SimulationRunner(ticks::incrementAndGet, INTERVAL, scheduler);

    runner.start();
    runner.start();

    verify(scheduler).scheduleAtFixedRate(any(), anyLong(), anyLong(), any());
  }

  @Test
  void stopBeforeStart_doesNothing() {
    new SimulationRunner(ticks::incrementAndGet, INTERVAL, scheduler).stop();

    verify(scheduler, never()).scheduleAtFixedRate(any(), anyLong(), anyLong(), any());
  }

  @Test
  void intervalOf_convertsScenarioSecondsExactly() {
    assertThat(SimulationRunner.intervalOf(10)).isEqualTo(Duration.ofSeconds(10));
    assertThat(SimulationRunner.intervalOf(0.5)).isEqualTo(Duration.ofMillis(500));
  }
}
