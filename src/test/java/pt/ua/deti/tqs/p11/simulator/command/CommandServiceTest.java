package pt.ua.deti.tqs.p11.simulator.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import pt.ua.deti.tqs.p11.simulator.config.HubConfig;
import pt.ua.deti.tqs.p11.simulator.config.ScenarioConfig;
import pt.ua.deti.tqs.p11.simulator.config.SimulationParams;
import pt.ua.deti.tqs.p11.simulator.config.VehicleConfig;
import pt.ua.deti.tqs.p11.simulator.config.VehicleType;
import pt.ua.deti.tqs.p11.simulator.engine.CommandOutcome;
import pt.ua.deti.tqs.p11.simulator.engine.SimulationEngine;
import pt.ua.deti.tqs.p11.simulator.telemetry.LockState;
import pt.ua.deti.tqs.p11.simulator.telemetry.Position;
import pt.ua.deti.tqs.p11.simulator.telemetry.VehicleTelemetry;

/**
 * The service between the MQTT adapter and the engine. No broker and no real threads: the
 * "simulation thread" is a list of tasks the test runs when it wants, the clock is set by hand, the
 * engine is the real one and the responses go to a list.
 */
class CommandServiceTest {
  private static final Instant NOW = Instant.parse("2026-11-10T10:10:00Z");
  private static final Instant DEADLINE = NOW.plusSeconds(30);

  private static final Position HUB_01 = new Position(40.6443, -8.6455);
  private static final Position HUB_02 = new Position(40.6412, -8.6501);

  /** One bike at hub-01, two hubs, no faults and no automatic trips. */
  private static final ScenarioConfig SCENARIO =
      new ScenarioConfig(
          "fleet-01",
          new SimulationParams(10, 7, 0, 0, 0, 0, 15),
          List.of(
              new HubConfig("hub-01", "Hub one", HUB_01),
              new HubConfig("hub-02", "Hub two", HUB_02)),
          List.of(new VehicleConfig("bike-0001", VehicleType.E_BIKE, "hub-01", 90)));

  private record Response(VehicleCommand command, CommandOutcome outcome) {}

  private final List<VehicleTelemetry> telemetry = new ArrayList<>();
  private final List<Response> responses = new ArrayList<>();
  private final List<Runnable> simulationThread = new ArrayList<>();
  private final MutableClock clock = new MutableClock(NOW);
  private int commandSequence;

  private final SimulationEngine engine =
      new SimulationEngine(
          SCENARIO,
          t -> {
            telemetry.add(t);
            return CompletableFuture.completedFuture(null);
          },
          new Random(7));

  private final CommandService service =
      new CommandService(
          engine,
          (command, outcome) -> {
            responses.add(new Response(command, outcome));
            return CompletableFuture.completedFuture(null);
          },
          clock,
          simulationThread::add);

  private VehicleCommand command(CommandName name, String vehicleId) {
    commandSequence++;
    return new VehicleCommand("fleet-01", vehicleId, name, new UUID(0, commandSequence), DEADLINE);
  }

  /** Runs what was handed to the simulation thread, as its executor would. */
  private void runSimulationThread() {
    List<Runnable> tasks = List.copyOf(simulationThread);
    simulationThread.clear();
    tasks.forEach(Runnable::run);
  }

  private void submitAndRun(VehicleCommand command) {
    service.submit(command);
    runSimulationThread();
  }

  private VehicleTelemetry lastTelemetry() {
    return telemetry.getLast();
  }

  @Test
  void submit_handsTheCommandToTheSimulationThread_andRespondsOnlyWhenItRuns() {
    VehicleCommand unlock = command(CommandName.UNLOCK, "bike-0001");

    service.submit(unlock);

    assertThat(responses).isEmpty();
    assertThat(simulationThread).hasSize(1);

    runSimulationThread();

    assertThat(responses).containsExactly(new Response(unlock, CommandOutcome.completed()));
  }

  @Test
  void unlock_isCompleted_andTheNextTelemetryShowsTheVehicleUnlocked() {
    VehicleCommand unlock = command(CommandName.UNLOCK, "bike-0001");
    engine.tick();

    submitAndRun(unlock);
    engine.tick();

    assertThat(responses).containsExactly(new Response(unlock, CommandOutcome.completed()));
    assertThat(lastTelemetry().lockState()).isEqualTo(LockState.UNLOCKED);
  }

  @Test
  void lockAtAHub_isCompleted_andTheVehicleIsAssociatedWithThatHub() {
    submitAndRun(command(CommandName.UNLOCK, "bike-0001"));

    submitAndRun(command(CommandName.LOCK, "bike-0001"));
    engine.tick();

    assertThat(responses.getLast().outcome()).isEqualTo(CommandOutcome.completed());
    assertThat(lastTelemetry().lockState()).isEqualTo(LockState.LOCKED);
    assertThat(lastTelemetry().hubId()).contains("hub-01");
  }

  @Test
  void lockBetweenHubs_isRejectedWithNotAtHub() {
    submitAndRun(command(CommandName.UNLOCK, "bike-0001"));
    engine.tick();

    submitAndRun(command(CommandName.LOCK, "bike-0001"));

    assertThat(responses.getLast().outcome()).isEqualTo(CommandOutcome.rejected("NOT_AT_HUB"));
  }

  @Test
  void ping_isCompleted_andChangesNothing() {
    submitAndRun(command(CommandName.PING, "bike-0001"));
    engine.tick();

    assertThat(responses.getLast().outcome()).isEqualTo(CommandOutcome.completed());
    assertThat(lastTelemetry().lockState()).isEqualTo(LockState.LOCKED);
  }

  @Test
  void commandToAnUnknownVehicle_isRejectedWithUnknownVehicle() {
    submitAndRun(command(CommandName.UNLOCK, "bike-9999"));

    assertThat(responses.getLast().outcome()).isEqualTo(CommandOutcome.rejected("UNKNOWN_VEHICLE"));
  }

  @Test
  void expiredCommand_isRejectedWithExpired_andNotApplied() {
    clock.set(DEADLINE.plusMillis(1));

    submitAndRun(command(CommandName.UNLOCK, "bike-0001"));
    engine.tick();

    assertThat(responses.getLast().outcome()).isEqualTo(CommandOutcome.rejected("EXPIRED"));
    assertThat(lastTelemetry().lockState()).isEqualTo(LockState.LOCKED);
  }

  /** A command that arrived on time is not rejected because the simulation thread was busy. */
  @Test
  void expiry_isCheckedWhenTheCommandIsReceived_notWhenItRuns() {
    clock.set(DEADLINE);
    service.submit(command(CommandName.UNLOCK, "bike-0001"));

    clock.set(DEADLINE.plusSeconds(5));
    runSimulationThread();

    assertThat(responses.getLast().outcome()).isEqualTo(CommandOutcome.completed());
  }

  @Test
  void everyCommand_receivesExactlyOneResponse_echoingIt_inTheOrderReceived() {
    VehicleCommand unlock = command(CommandName.UNLOCK, "bike-0001");
    VehicleCommand unknown = command(CommandName.LOCK, "bike-9999");
    VehicleCommand ping = command(CommandName.PING, "bike-0001");

    service.submit(unlock);
    service.submit(unknown);
    service.submit(ping);
    runSimulationThread();

    assertThat(responses).extracting(Response::command).containsExactly(unlock, unknown, ping);
  }

  /** A clock the test moves by hand. */
  private static final class MutableClock extends Clock {
    private Instant now;

    private MutableClock(Instant now) {
      this.now = now;
    }

    private void set(Instant instant) {
      now = instant;
    }

    @Override
    public Instant instant() {
      return now;
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }
  }
}
