# Fleet telemetry simulator

A separate service that simulates a fleet of light vehicles, publishes their telemetry over MQTT and
accepts lock, unlock and ping commands, following the interface contract in
[contract/](contract/README.md). Any platform that implements the contract can consume it.

## Configuration

The broker connection is set with environment variables:

| Variable | Default | Meaning |
| --- | --- | --- |
| `SIMULATOR_MQTT_HOST` | `localhost` | Broker host |
| `SIMULATOR_MQTT_PORT` | `1883` | Broker port |
| `SIMULATOR_MQTT_USERNAME` | empty | Username; empty connects without authentication |
| `SIMULATOR_MQTT_PASSWORD` | empty | Password, used with the username |
| `SIMULATOR_MQTT_CLIENT_ID` | generated | MQTT client id; must be unique per broker |
| `SIMULATOR_SCENARIO_FILE` | `classpath:scenarios/ria-ride.yml` | Scenario file (hubs, vehicles, simulation parameters). Use `file:/config/scenario.yml` for a file mounted into the container. See [Example scenarios](#example-scenarios) for the ones shipped with the simulator |

If the broker is unreachable or the connection drops, the simulator keeps retrying (from 1 s up to 2 min between attempts) and resends what it could not deliver.

## How telemetry is published

Each vehicle state becomes a contract `telemetry` message (envelope with `schemaVersion`, a new
`messageId`, a UTC `timestamp`, `fleetId` and `vehicleId`), published as **retained, QoS 1** on
`fleet/{fleetId}/vehicles/{vehicleId}/telemetry`. The tests validate every built message against
the JSON Schemas in `contract/schemas`, including the messages of vehicles that are riding, and an
integration test reads it back from a real Mosquitto broker.

## How the simulation runs

At start-up the simulator begins a run that ticks every `simulation.emissionIntervalSeconds` (fixed
rate, first tick immediately). On each tick every vehicle, in scenario order, publishes one
telemetry message and then moves one interval forward. The first reading of a vehicle is its state
in the scenario: parked and locked at its hub.

A vehicle is always in one of three states:

| State | Reported as | Battery per interval |
| --- | --- | --- |
| Parked | `locked`, the hub's `hubId` and position, speed 0 | charges by `simulation.chargePercentPerMinute`, up to 100 % |
| Riding | `unlocked`, `hubId` null, speed `simulation.rideSpeedKmh` | drains by `simulation.drainPercentPerMinute`, down to 0 % |
| Waiting at a hub | `unlocked`, the hub's `hubId` and position, speed 0 | does not change |

**Trips.** A trip goes from one hub to another hub at a different place, chosen at random. The
position moves along the straight line between the two hubs, a little closer to the destination on
every reading, so it never leaves the area of the configured hubs. A trip lasts the distance between
the hubs (along the Earth's surface, Haversine formula) divided by the ride speed. On arrival the
vehicle is at the destination hub's exact position. A trip starts in one of two ways:

- **Automatic trips.** After each reading, a parked vehicle without a fault starts a trip with
  `simulation.tripProbability`. On arrival it is locked at the destination hub, and it is always
  reported parked at least once before it leaves again.
- **Trips started by the platform**, with an `unlock` command (see below). On arrival the vehicle
  waits at the destination hub, unlocked, until the platform sends `lock`.

A scenario with a single hub has no trips: a vehicle unlocked there waits at its own hub.

**Faults.** A healthy vehicle gets a fault with `simulation.faultProbability` per tick; once raised,
a fault stays for the rest of the run. A faulty vehicle does not start automatic trips and cannot
be unlocked.

Fault codes this simulator raises (the contract lets each simulator define its own):

| Code | Meaning |
| --- | --- |
| `BRAKE_WEAR` | Brakes need service |
| `FLAT_TIRE` | A tyre is flat |
| `BATTERY_OVERHEAT` | Battery temperature too high |

**Reproducible runs.** All randomness (faults, trips and message ids) comes from one generator
seeded with `simulation.seed`, and all time from one injected clock. Two runs with the same seed and
scenario publish the same sequence of messages; only the timestamps follow the real clock. Commands
take part in the same sequence, so a run with commands repeats only if the same commands arrive
between the same ticks. A scenario without trips (`tripProbability` 0) draws no random number for
trips. The engine is tested without a broker and without real time.

## How commands are handled

The simulator subscribes to `fleet/{fleetId}/vehicles/+/commands/+` (QoS 1) and answers every
command once, on `fleet/{fleetId}/vehicles/{vehicleId}/responses` (QoS 1, not retained), with the
`commandId` of the command.

| Command | Parked | Riding | Waiting at a hub |
| --- | --- | --- | --- |
| `unlock` | Starts a trip to another hub. A faulty vehicle is rejected with `VEHICLE_FAULT` | `COMPLETED`, nothing changes (an automatic trip is handed over to the platform: it waits for a lock on arrival) | `COMPLETED`, nothing changes |
| `lock` | `COMPLETED`, nothing changes | Rejected with `NOT_AT_HUB`, except before the vehicle has moved away from the hub it left: then it is locked there | Locks the vehicle at that hub |
| `ping` | `COMPLETED` | `COMPLETED` | `COMPLETED` |

The next telemetry message of the vehicle shows the change.

- **Idempotent.** As the contract requires, unlocking an unlocked vehicle or locking a locked one
  changes nothing and is answered `COMPLETED`, so a platform can safely retry after a timeout.
- **Rejection reasons**, in `UPPER_SNAKE_CASE`:

  | Reason | When |
  | --- | --- |
  | `UNKNOWN_VEHICLE` | The vehicle is not in the scenario (any command) |
  | `EXPIRED` | The command was received after its `expiresAt`; it is not applied. At exactly `expiresAt` it is still on time |
  | `NOT_AT_HUB` | A `lock` while the vehicle is between hubs |
  | `VEHICLE_FAULT` | An `unlock` of a vehicle with an active fault |

- **Invalid messages** (not JSON, missing or malformed `commandId`, `expiresAt` not in UTC, another
  major `schemaVersion`, `fleetId`/`vehicleId` different from the topic, unknown command) get no
  response, since none can be built without a valid `commandId`. They are logged with the reason and
  dropped. Unknown fields and newer minor versions are accepted (tolerant reader).
- **One thread.** Commands are applied on the same single thread that runs the ticks, in the order
  they arrive, so a command never runs at the same time as a tick.

To try it by hand with Mosquitto's command-line tools, with the simulator running:

```bash
mosquitto_sub -h localhost -v -t 'fleet/fleet-01/vehicles/bike-0001/#'
mosquitto_pub -h localhost -t fleet/fleet-01/vehicles/bike-0001/commands/unlock \
  -m '{"schemaVersion":"1.0.0","messageId":"c3a1f8d0-6a24-4f67-b1d2-9e3e4a5b6c03","type":"command","timestamp":"2026-11-17T17:00:00Z","fleetId":"fleet-01","vehicleId":"bike-0001","data":{"commandId":"e5b2a9c1-0d3f-4a8e-8b77-1c2d3e4f5a04"}}'
```

## Scenario file

YAML read at start-up; examples in `src/main/resources/scenarios/`. If the file is missing or
breaks a rule, the simulator does not start and the log lists every problem with its field path.

| Field | Meaning |
| --- | --- |
| `fleetId` | Fleet identifier, used in every topic and message |
| `simulation.emissionIntervalSeconds` | Seconds between readings of a vehicle, greater than 0 |
| `simulation.seed` | Whole number; the same seed gives the same run |
| `simulation.faultProbability` | Chance of a fault per reading, from 0 to 1 |
| `simulation.drainPercentPerMinute` | Optional, default 0. Battery lost per minute while a vehicle is riding, zero or more |
| `simulation.chargePercentPerMinute` | Optional, default 0. Battery gained per minute while a vehicle is parked and locked at a hub, zero or more |
| `simulation.tripProbability` | Optional, default 0 (no automatic trips). Chance per reading that a parked vehicle starts a trip to another hub, from 0 to 1 |
| `simulation.rideSpeedKmh` | Optional, default 15. Speed of a vehicle during a trip, greater than 0 |
| `hubs[]` | `id`, `name`, `position` (`lat` -90 to 90, `lon` -180 to 180) |
| `vehicles[]` | `id`, `type` (`E_BIKE` or `E_SCOOTER`), `hub` (an id from `hubs`), `battery` (0 to 100) |

Identifiers match `[A-Za-z0-9_-]{1,64}` and are unique within hubs and within vehicles. The vehicle
type is internal to the simulator and never appears in the contract messages.

### Example scenarios

Select one with `SIMULATOR_SCENARIO_FILE=classpath:scenarios/<file>`.

| File | What it shows |
| --- | --- |
| `ria-ride.yml` (default) | 6 hubs in Aveiro and 20 parked vehicles, with batteries of 19, 20 and 21 % around the minimum battery to book (rule BR1) and rare faults. Vehicles move only when the platform unlocks them |
| `ria-ride-charging.yml` | The same fleet with a fast charge rate, to watch the batteries rise to 100 % |
| `ria-ride-trips.yml` | The same fleet riding between hubs on its own, to watch vehicles move on a map: the first one leaves within seconds and reaches another hub in about 2 minutes |
| `minimal.yml` | One hub and one vehicle, for quick local runs |

## Build and test

Requires Java 21 (the Maven wrapper downloads Maven).

```bash
./mvnw clean verify      # unit and integration tests (need Docker), coverage, formatting
./mvnw test -Pcontract   # contract tests only: every emitted message against contract/schemas
./mvnw spotless:apply    # fix formatting (Google Java Style)
npm ci --prefix contract && npm run validate --prefix contract   # contract schemas and examples
```

## Docker image

Every merge to `main` publishes `ghcr.io/o-reis/tqs-telemetry-simulator` with the tags `latest`
and the commit SHA. To build it locally: `docker build -t telemetry-simulator .`