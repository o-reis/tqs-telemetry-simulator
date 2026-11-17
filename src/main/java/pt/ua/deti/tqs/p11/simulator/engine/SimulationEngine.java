package pt.ua.deti.tqs.p11.simulator.engine;

import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;
import pt.ua.deti.tqs.p11.simulator.config.HubConfig;
import pt.ua.deti.tqs.p11.simulator.config.ScenarioConfig;
import pt.ua.deti.tqs.p11.simulator.config.SimulationParams;
import pt.ua.deti.tqs.p11.simulator.telemetry.LockState;
import pt.ua.deti.tqs.p11.simulator.telemetry.Position;
import pt.ua.deti.tqs.p11.simulator.telemetry.TelemetryPublisher;
import pt.ua.deti.tqs.p11.simulator.telemetry.VehicleTelemetry;

/**
 * Main loop of the simulation. Each {@link #tick()} publishes the state of every vehicle through
 * the {@link TelemetryPublisher} port and then advances it by one emission interval.
 *
 * <p>The engine never reads the time and owns no thread: {@link SimulationRunner} calls {@code
 * tick()} at the configured rate, and tests call it directly. All randomness comes from the one
 * {@link RandomGenerator} it is given, and vehicles are visited in scenario order, so the same seed
 * and scenario always publish the same sequence.
 *
 * <p>Vehicles stay parked and locked in their hub. On each tick a healthy vehicle gets a fault with
 * the scenario's {@code faultProbability}; a fault, once raised, stays.
 *
 * <p>The battery follows the {@link BatteryModel} of the scenario's rates. The first reading of a
 * vehicle has the battery of the scenario; after each reading the battery moves by one interval.
 * The model draws no random numbers, so the rates never change the sequence of faults.
 */
public class SimulationEngine {

  /** Fault codes this simulator raises (the contract lets each simulator define its own). */
  static final List<String> FAULT_CODES = List.of("BRAKE_WEAR", "FLAT_TIRE", "BATTERY_OVERHEAT");

  private static final double PARKED_SPEED_KMH = 0.0;

  /** Every vehicle stays locked in its hub, so this is its only activity until trips exist. */
  private static final BatteryModel.Activity ACTIVITY = BatteryModel.Activity.PARKED_AT_HUB;

  private final String fleetId;
  private final double faultProbability;
  private final double intervalSeconds;
  private final BatteryModel batteryModel;
  private final TelemetryPublisher publisher;
  private final RandomGenerator random;
  private final List<SimulatedVehicle> vehicles;

  /**
   * @param random the run's only source of randomness; share the same instance with anything else
   *     that must be reproducible (message ids)
   */
  public SimulationEngine(
      ScenarioConfig scenario, TelemetryPublisher publisher, RandomGenerator random) {
    this.fleetId = scenario.fleetId();
    SimulationParams params = scenario.simulation();
    this.faultProbability = params.faultProbability();
    this.intervalSeconds = params.emissionIntervalSeconds();
    this.batteryModel =
        new BatteryModel(params.drainPercentPerMinute(), params.chargePercentPerMinute());
    this.publisher = publisher;
    this.random = random;
    // Hub ids are unique and every vehicle's hub exists: ScenarioParser checks both.
    Map<String, Position> hubPositions =
        scenario.hubs().stream().collect(Collectors.toMap(HubConfig::id, HubConfig::position));
    this.vehicles =
        scenario.vehicles().stream()
            .map(v ->
                new SimulatedVehicle(v.id(), v.hubId(), hubPositions.get(v.hubId()), v.battery()))
            .toList();
  }

  /** Publishes one reading per vehicle, then advances every vehicle by one interval. */
  public void tick() {
    for (SimulatedVehicle vehicle : vehicles) {
      maybeRaiseFault(vehicle);
      publisher.publish(telemetryOf(vehicle));
      vehicle.batteryPercent = batteryModel.next(vehicle.batteryPercent, ACTIVITY, intervalSeconds);
    }
  }

  private void maybeRaiseFault(SimulatedVehicle vehicle) {
    // nextDouble() is in [0, 1): a probability of 0 never raises a fault, 1 always does.
    if (vehicle.faultCode == null && random.nextDouble() < faultProbability) {
      vehicle.faultCode = FAULT_CODES.get(random.nextInt(FAULT_CODES.size()));
    }
  }

  private VehicleTelemetry telemetryOf(SimulatedVehicle vehicle) {
    return new VehicleTelemetry(
        fleetId,
        vehicle.id,
        vehicle.batteryPercent,
        LockState.LOCKED,
        vehicle.hubPosition,
        vehicle.hubId,
        vehicle.faultCode,
        PARKED_SPEED_KMH);
  }

  /** State of one vehicle during a run. Only the thread that calls tick() touches it. */
  private static final class SimulatedVehicle {
    private final String id;
    private final String hubId;
    private final Position hubPosition;
    private double batteryPercent;
    private String faultCode;

    private SimulatedVehicle(String id, String hubId, Position hubPosition, double batteryPercent) {
      this.id = id;
      this.hubId = hubId;
      this.hubPosition = hubPosition;
      this.batteryPercent = batteryPercent;
    }
  }
}
