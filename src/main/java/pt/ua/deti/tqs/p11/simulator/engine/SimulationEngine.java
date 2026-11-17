package pt.ua.deti.tqs.p11.simulator.engine;

import java.util.LinkedHashMap;
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
 * <p>The engine is not thread-safe: {@code tick()} and the commands ({@link #unlock}, {@link
 * #lock}, {@link #ping}) must all be called from the same thread.
 *
 * <p>Faults: on each tick a healthy vehicle gets a fault with the scenario's {@code
 * faultProbability}; a fault, once raised, stays.
 *
 * <p>Automatic trips: a vehicle parked and locked at a hub, without a fault, starts a trip with the
 * scenario's {@code tripProbability} after each reading. Its destination is another hub, at a
 * different place, chosen at random. During the trip it is unlocked, in no hub, at the ride speed,
 * and its position moves along the straight line between the two hubs ({@link Trip}). When the trip
 * ends it is locked at the destination hub, at that hub's exact position. A vehicle that just
 * arrived is always reported parked at least once before it leaves again. With a {@code
 * tripProbability} of 0 the engine draws no random number for trips.
 *
 * <p>Commands from the platform: {@code unlock} starts a trip to another hub chosen at random; on
 * arrival the vehicle waits there, unlocked, until {@code lock}. A vehicle can only be locked at a
 * hub: when it is waiting at one, or before it has moved away from the hub it left. Commands are
 * idempotent, as the contract requires: unlocking an unlocked vehicle or locking a locked one
 * changes nothing and is completed. Unlocking a vehicle on an automatic trip hands that trip over
 * to the platform: it no longer locks on arrival.
 *
 * <p>The battery follows the {@link BatteryModel} of the scenario's rates: it drains while riding,
 * charges while parked and does not change while waiting unlocked at a hub. The first reading of a
 * vehicle has the battery of the scenario; after each reading the battery moves by one interval.
 * The model draws no random numbers.
 */
public class SimulationEngine {

  /** Fault codes this simulator raises (the contract lets each simulator define its own). */
  static final List<String> FAULT_CODES = List.of("BRAKE_WEAR", "FLAT_TIRE", "BATTERY_OVERHEAT");

  private static final double STOPPED_SPEED_KMH = 0.0;

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
  private final Map<String, SimulatedVehicle> vehiclesById;

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
    // Vehicle ids are unique (ScenarioParser checks it).
    this.vehiclesById = new LinkedHashMap<>();
    vehicles.forEach(v -> vehiclesById.put(v.id, v));
  }

  /** Publishes one reading per vehicle, then advances every vehicle by one interval. */
  public void tick() {
    for (SimulatedVehicle vehicle : vehicles) {
      maybeRaiseFault(vehicle);
      publisher.publish(telemetryOf(vehicle));
      advance(vehicle);
    }
  }

  /**
   * Unlocks a vehicle for the platform. A parked vehicle starts a trip to another hub chosen at
   * random (or, with no other hub, waits unlocked at its own); an unlocked one stays as it is.
   *
   * @return completed, or rejected with {@code UNKNOWN_VEHICLE} or {@code VEHICLE_FAULT}
   */
  public CommandOutcome unlock(String vehicleId) {
    SimulatedVehicle vehicle = vehiclesById.get(vehicleId);
    if (vehicle == null) {
      return CommandOutcome.rejected(CommandOutcome.UNKNOWN_VEHICLE);
    }
    switch (vehicle.state) {
      case PARKED -> {
        if (vehicle.faultCode != null) {
          return CommandOutcome.rejected(CommandOutcome.VEHICLE_FAULT);
        }
        startPlatformTrip(vehicle);
      }
      // Already unlocked: nothing changes, except that an automatic trip is handed over.
      case RIDING -> vehicle.platformTrip = true;
      case WAITING_AT_HUB -> {
        // Already unlocked and at a hub: nothing to do.
      }
    }
    return CommandOutcome.completed();
  }

  /**
   * Locks a vehicle at the hub where it is. A vehicle between hubs cannot be locked.
   *
   * @return completed, or rejected with {@code UNKNOWN_VEHICLE} or {@code NOT_AT_HUB}
   */
  public CommandOutcome lock(String vehicleId) {
    SimulatedVehicle vehicle = vehiclesById.get(vehicleId);
    if (vehicle == null) {
      return CommandOutcome.rejected(CommandOutcome.UNKNOWN_VEHICLE);
    }
    switch (vehicle.state) {
      case PARKED -> {
        // Already locked: nothing to do.
      }
      case WAITING_AT_HUB -> vehicle.park();
      case RIDING -> {
        // At 0 s the vehicle is still at the hub it is leaving; after that it is between hubs.
        if (vehicle.tripElapsedSeconds > 0) {
          return CommandOutcome.rejected(CommandOutcome.NOT_AT_HUB);
        }
        vehicle.park();
      }
    }
    return CommandOutcome.completed();
  }

  /**
   * Checks that a vehicle is reachable; changes nothing.
   *
   * @return completed, or rejected with {@code UNKNOWN_VEHICLE}
   */
  public CommandOutcome ping(String vehicleId) {
    return vehiclesById.containsKey(vehicleId)
        ? CommandOutcome.completed()
        : CommandOutcome.rejected(CommandOutcome.UNKNOWN_VEHICLE);
  }

  private void maybeRaiseFault(SimulatedVehicle vehicle) {
    // nextDouble() is in [0, 1): a probability of 0 never raises a fault, 1 always does.
    if (vehicle.faultCode == null && random.nextDouble() < faultProbability) {
      vehicle.faultCode = FAULT_CODES.get(random.nextInt(FAULT_CODES.size()));
    }
  }

  /** Moves the vehicle one interval forward: battery, then trip progress or a new trip. */
  private void advance(SimulatedVehicle vehicle) {
    switch (vehicle.state) {
      case RIDING -> {
        vehicle.batteryPercent =
            batteryModel.next(vehicle.batteryPercent, Activity.RIDING, intervalSeconds);
        vehicle.tripElapsedSeconds += intervalSeconds;
        if (vehicle.trip.isFinished(vehicle.tripElapsedSeconds)) {
          vehicle.arrive();
        }
      }
      case PARKED -> {
        vehicle.batteryPercent =
            batteryModel.next(vehicle.batteryPercent, Activity.PARKED_AT_HUB, intervalSeconds);
        maybeStartAutomaticTrip(vehicle);
      }
      case WAITING_AT_HUB ->
          vehicle.batteryPercent =
              batteryModel.next(vehicle.batteryPercent, Activity.OTHER, intervalSeconds);
    }
  }

  private void maybeStartAutomaticTrip(SimulatedVehicle vehicle) {
    // Checked before any draw: without trips, or for a faulty vehicle, no random number is used.
    if (tripProbability == 0 || vehicle.faultCode != null) {
      return;
    }
    List<HubConfig> destinations = destinationsFrom(vehicle);
    if (destinations.isEmpty()) {
      return;
    }
    if (random.nextDouble() < tripProbability) {
      vehicle.startTrip(tripTo(vehicle, randomHub(destinations)), false);
    }
  }

  private void startPlatformTrip(SimulatedVehicle vehicle) {
    List<HubConfig> destinations = destinationsFrom(vehicle);
    if (destinations.isEmpty()) {
      vehicle.state = VehicleState.WAITING_AT_HUB;
      return;
    }
    vehicle.startTrip(tripTo(vehicle, randomHub(destinations)), true);
  }

  /** Hubs at a different place than the vehicle's (Trip.between rejects the same place). */
  private List<HubConfig> destinationsFrom(SimulatedVehicle vehicle) {
    return hubs.stream().filter(hub -> !hub.position().equals(vehicle.hubPosition)).toList();
  }

  private HubConfig randomHub(List<HubConfig> candidates) {
    return candidates.get(random.nextInt(candidates.size()));
  }

  private PlannedTrip tripTo(SimulatedVehicle vehicle, HubConfig destination) {
    return new PlannedTrip(
        Trip.between(vehicle.hubPosition, destination.position(), rideSpeedKmh), destination.id());
  }

  private VehicleTelemetry telemetryOf(SimulatedVehicle vehicle) {
    return switch (vehicle.state) {
      case PARKED ->
          new VehicleTelemetry(
              fleetId,
              vehicle.id,
              vehicle.batteryPercent,
              LockState.LOCKED,
              vehicle.hubPosition,
              vehicle.hubId,
              vehicle.faultCode,
              STOPPED_SPEED_KMH);
      case WAITING_AT_HUB ->
          new VehicleTelemetry(
              fleetId,
              vehicle.id,
              vehicle.batteryPercent,
              LockState.UNLOCKED,
              vehicle.hubPosition,
              vehicle.hubId,
              vehicle.faultCode,
              STOPPED_SPEED_KMH);
      case RIDING ->
          new VehicleTelemetry(
              fleetId,
              vehicle.id,
              vehicle.batteryPercent,
              LockState.UNLOCKED,
              vehicle.trip.positionAt(vehicle.tripElapsedSeconds),
              null,
              vehicle.faultCode,
              rideSpeedKmh);
    };
  }

  /** What a vehicle is doing. */
  private enum VehicleState {
    /** Locked at a hub. */
    PARKED,
    /** On a trip between two hubs, unlocked. */
    RIDING,
    /** Unlocked at a hub after a platform trip, until the platform locks it. */
    WAITING_AT_HUB
  }

  /** A trip and the id of the hub where it ends. */
  private record PlannedTrip(Trip trip, String destinationHubId) {}

  /** State of one vehicle during a run. Only the thread that calls tick() touches it. */
  private static final class SimulatedVehicle {
    private final String id;

    /** Hub where the vehicle is, or where its current trip started. */
    private String hubId;

    private Position hubPosition;
    private double batteryPercent;
    private String faultCode;
    private VehicleState state = VehicleState.PARKED;

    /** The trip in progress, or {@code null} unless riding. */
    private Trip trip;

    private String destinationHubId;
    private double tripElapsedSeconds;

    /** Whether the platform unlocked the vehicle: then it waits for a lock on arrival. */
    private boolean platformTrip;

    private SimulatedVehicle(String id, String hubId, Position hubPosition, double batteryPercent) {
      this.id = id;
      this.hubId = hubId;
      this.hubPosition = hubPosition;
      this.batteryPercent = batteryPercent;
    }

    private void startTrip(PlannedTrip planned, boolean byPlatform) {
      state = VehicleState.RIDING;
      trip = planned.trip();
      destinationHubId = planned.destinationHubId();
      tripElapsedSeconds = 0;
      platformTrip = byPlatform;
    }

    /** Reaches the destination, at the hub's exact position. */
    private void arrive() {
      hubId = destinationHubId;
      hubPosition = trip.destination();
      state = platformTrip ? VehicleState.WAITING_AT_HUB : VehicleState.PARKED;
      clearTrip();
    }

    /** Locks the vehicle at {@link #hubId}. */
    private void park() {
      state = VehicleState.PARKED;
      clearTrip();
    }

    private void clearTrip() {
      trip = null;
      destinationHubId = null;
      tripElapsedSeconds = 0;
      platformTrip = false;
    }
  }
}
