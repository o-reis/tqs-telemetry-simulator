package pt.ua.deti.tqs.p11.simulator.engine;

import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;
import pt.ua.deti.tqs.p11.simulator.config.HubConfig;
import pt.ua.deti.tqs.p11.simulator.config.ScenarioConfig;
import pt.ua.deti.tqs.p11.simulator.config.SimulationParams;
import pt.ua.deti.tqs.p11.simulator.engine.BatteryModel.Activity;
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
 * <p>Faults: on each tick a healthy vehicle gets a fault with the scenario's {@code
 * faultProbability}; a fault, once raised, stays.
 *
 * <p>Trips: a vehicle parked and locked at a hub, without a fault, starts a trip with the
 * scenario's {@code tripProbability} after each reading. Its destination is another hub, at a
 * different place, chosen at random. During the trip it is unlocked, in no hub, at the ride speed,
 * and its position moves along the straight line between the two hubs ({@link Trip}). When the
 * trip ends it is locked at the destination hub, at that hub's exact position. A vehicle that just
 * arrived is always reported parked at least once before it leaves again. With a {@code
 * tripProbability} of 0 the engine draws no random number for trips, so a scenario without trips
 * publishes the same sequence as before trips existed.
 *
 * <p>The battery follows the {@link BatteryModel} of the scenario's rates: it drains while riding
 * and charges while parked. The first reading of a vehicle has the battery of the scenario; after
 * each reading the battery moves by one interval. The model draws no random numbers.
 */
public class SimulationEngine {

  /** Fault codes this simulator raises (the contract lets each simulator define its own). */
  static final List<String> FAULT_CODES = List.of("BRAKE_WEAR", "FLAT_TIRE", "BATTERY_OVERHEAT");

  private static final double PARKED_SPEED_KMH = 0.0;

  private final String fleetId;
  private final double faultProbability;
  private final double tripProbability;
  private final double rideSpeedKmh;
  private final double intervalSeconds;
  private final BatteryModel batteryModel;
  private final TelemetryPublisher publisher;
  private final RandomGenerator random;
  private final List<HubConfig> hubs;
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
    this.tripProbability = params.tripProbability();
    this.rideSpeedKmh = params.rideSpeedKmh();
    this.intervalSeconds = params.emissionIntervalSeconds();
    this.batteryModel =
        new BatteryModel(params.drainPercentPerMinute(), params.chargePercentPerMinute());
    this.publisher = publisher;
    this.random = random;
    this.hubs = List.copyOf(scenario.hubs());
    // Hub ids are unique and every vehicle's hub exists: ScenarioParser checks both.
    Map<String, Position> hubPositions =
        hubs.stream().collect(Collectors.toMap(HubConfig::id, HubConfig::position));
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
      advance(vehicle);
    }
  }

  private void maybeRaiseFault(SimulatedVehicle vehicle) {
    // nextDouble() is in [0, 1): a probability of 0 never raises a fault, 1 always does.
    if (vehicle.faultCode == null && random.nextDouble() < faultProbability) {
      vehicle.faultCode = FAULT_CODES.get(random.nextInt(FAULT_CODES.size()));
    }
  }

  /** Moves the vehicle one interval forward: battery, then trip progress or a new trip. */
  private void advance(SimulatedVehicle vehicle) {
    if (vehicle.isRiding()) {
      vehicle.batteryPercent =
          batteryModel.next(vehicle.batteryPercent, Activity.RIDING, intervalSeconds);
      vehicle.tripElapsedSeconds += intervalSeconds;
      if (vehicle.trip.isFinished(vehicle.tripElapsedSeconds)) {
        vehicle.arrive();
      }
    } else {
      vehicle.batteryPercent =
          batteryModel.next(vehicle.batteryPercent, Activity.PARKED_AT_HUB, intervalSeconds);
      maybeStartTrip(vehicle);
    }
  }

  private void maybeStartTrip(SimulatedVehicle vehicle) {
    // Checked before any draw: without trips, or for a faulty vehicle, no random number is used.
    if (tripProbability == 0 || vehicle.faultCode != null) {
      return;
    }
    // A hub at the same place as the current one is not a trip (Trip.between rejects it).
    List<HubConfig> destinations =
        hubs.stream().filter(hub -> !hub.position().equals(vehicle.hubPosition)).toList();
    if (destinations.isEmpty()) {
      return;
    }
    if (random.nextDouble() < tripProbability) {
      HubConfig destination = destinations.get(random.nextInt(destinations.size()));
      vehicle.startTrip(
          Trip.between(vehicle.hubPosition, destination.position(), rideSpeedKmh),
          destination.id());
    }
  }

  private VehicleTelemetry telemetryOf(SimulatedVehicle vehicle) {
    if (vehicle.isRiding()) {
      return new VehicleTelemetry(
          fleetId,
          vehicle.id,
          vehicle.batteryPercent,
          LockState.UNLOCKED,
          vehicle.trip.positionAt(vehicle.tripElapsedSeconds),
          null,
          vehicle.faultCode,
          rideSpeedKmh);
    }
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
    private String hubId;
    private Position hubPosition;
    private double batteryPercent;
    private String faultCode;
    private Trip trip;
    private String destinationHubId;
    private double tripElapsedSeconds;

    private SimulatedVehicle(String id, String hubId, Position hubPosition, double batteryPercent) {
      this.id = id;
      this.hubId = hubId;
      this.hubPosition = hubPosition;
      this.batteryPercent = batteryPercent;
    }

    private boolean isRiding() {
      return trip != null;
    }

    private void startTrip(Trip newTrip, String destinationId) {
      trip = newTrip;
      destinationHubId = destinationId;
      tripElapsedSeconds = 0;
    }

    private void arrive() {
      hubId = destinationHubId;
      hubPosition = trip.destination();
      trip = null;
      destinationHubId = null;
      tripElapsedSeconds = 0;
    }
  }
}