# Fleet telemetry simulator

A separate service that simulates a fleet of light vehicles, publishes their telemetry over MQTT and
accepts lock, unlock and ping commands, following the interface contract in
[contract/](contract/README.md). Any platform that implements the contract can consume it.

## Quick start

You need only Docker with Docker Compose. In an empty folder (Bash, or Git Bash on Windows):

```bash
curl -O https://raw.githubusercontent.com/o-reis/tqs-telemetry-simulator/main/examples/docker-compose.yml
docker compose up -d
docker compose exec broker mosquitto_sub -v -t 'fleet/#'
```

The last command prints every message the simulator publishes: one telemetry message per vehicle
every 10 s, from the default scenario (20 vehicles at 6 hubs). Stop it with Ctrl+C, and stop the
simulator and the broker with `docker compose down`.

Your platform connects to the broker at `localhost:1883`, or `broker:1883` from a container on the
same Compose network. To run another scenario shipped in the image, set `SIMULATOR_SCENARIO_FILE`
(see [Example scenarios](#example-scenarios)), for example
`SIMULATOR_SCENARIO_FILE=classpath:scenarios/ria-ride-trips.yml docker compose up -d`, or put the
line `SIMULATOR_SCENARIO_FILE=classpath:scenarios/ria-ride-trips.yml` in a `.env` file next to
`docker-compose.yml`.

**Your own scenario.** Download the commented template
[examples/scenario.yml](examples/scenario.yml) next to `docker-compose.yml`, change it, uncomment
the `volumes` lines in `docker-compose.yml` and start with
`SIMULATOR_SCENARIO_FILE=file:/config/scenario.yml`.

**Pin a version.** The compose file uses the `latest` image. For tests that must not change by
surprise, set `SIMULATOR_VERSION` to a release, for example `SIMULATOR_VERSION=1.0.0` (see
[Releases](#releases)).

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
be unlocked. The scenario can also script faults (`faults[]`, see below): a fault raised at a set
time, optionally cleared after a set time. With no scripted fault and a `faultProbability` of 0,
no fault ever happens.

**Fault events.** Each time a fault appears or goes away, the simulator publishes an `event` message
(QoS 1, not retained) on `fleet/{fleetId}/vehicles/{vehicleId}/events`, with `eventType`
`FAULT_RAISED` or `FAULT_CLEARED`, the `faultCode` and a `severity`. The vehicle's telemetry keeps
carrying the current `faultCode`, so a lost event is still caught by the next reading. A fault is
reported once when it appears, not on every tick while it stays.

| Code | Severity |
| --- | --- |
| `BRAKE_WEAR` | `WARNING` |
| `FLAT_TIRE` | `WARNING` |
| `BATTERY_OVERHEAT` | `CRITICAL` |

**Scripted faults.** They make a test deterministic and draw no random number, so they never move
the seeded sequence of the random faults. Times are simulated seconds since the start of the run
and are applied at the first tick at or after the time. A scripted fault that comes due while the
vehicle already has a fault waits until the vehicle is healthy (clear the first one with
`clearAfterSeconds`), so use a `faultProbability` of 0 in a scenario that relies on them: a random
fault never clears and would block them.

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

## High-rate mode (load tests)

The simulator can emit at high rates with large fleets, so a platform can check its telemetry SLO
under load (SLO2: telemetry lag under 5 s at 10 readings/s).

- **Through configuration only.** The rate is the number of vehicles divided by
  `simulation.emissionIntervalSeconds`. A large fleet comes from `generatedVehicles.count`, so no
  long vehicle list is needed. `load-test.yml` gives 1000 vehicles at one reading per second; for
  another load, copy it, change those two values and select the file with
  `SIMULATOR_SCENARIO_FILE` (for example `file:/config/load.yml` mounted into the container).
- **Effective rate in the log.** At start-up the simulator logs the target rate; then every 10 s
  the effective rate, counting the readings the broker acknowledged (QoS 1); and when it stops, a
  summary of the whole run:

  ```
  Target rate: 1000.0 readings/s, a report every 10 s
  Effective rate: 999.6 readings/s (target 1000.0): 9997 sent, 9996 acknowledged, 0 failed in 10.0 s
  Run summary: Effective rate: 999.4 readings/s (target 1000.0): ...
  ```

  A period under 95 % of the target is logged as a warning: the configured rate is not sustained,
  usually because the broker or the network cannot keep up.
- **Tested.** `HighRateIT` runs `load-test.yml` in real time for 6 s through a real Mosquitto
  broker and requires no failed reading and at least the SLO rate, both as acknowledged by the
  broker and as received by a platform client. On a developer laptop it measured 999.2 readings/s
  acknowledged and 999.7 readings/s received, with 0 failed.

```bash
docker run --rm -e SIMULATOR_MQTT_HOST=broker \
  -e SIMULATOR_SCENARIO_FILE=classpath:scenarios/load-test.yml \
  ghcr.io/o-reis/tqs-telemetry-simulator:latest
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
| `faults[]` | Optional. Scripted faults, each with `vehicle` (an id from `vehicles`), `code` (one of the fault codes above), `atSeconds` (zero or more) and optionally `clearAfterSeconds` (greater than 0; without it the fault stays for the rest of the run) |
| `generatedVehicles` | Optional. `count` (1 to 100000) and `idPrefix` (optional, default `vehicle`): generates that many vehicles, `{idPrefix}-0001` onwards, spread over the hubs in turn, alternating `E_BIKE` and `E_SCOOTER`, with batteries from 20 to 100 %. Deterministic (no random numbers). With it, `vehicles` becomes optional; both can be used together, the listed vehicles first |

Identifiers match `[A-Za-z0-9_-]{1,64}` and are unique within hubs and within vehicles. The vehicle
type is internal to the simulator and never appears in the contract messages.

### Example scenarios

Select one with `SIMULATOR_SCENARIO_FILE=classpath:scenarios/<file>`.

| File | What it shows |
| --- | --- |
| `ria-ride.yml` (default) | 6 hubs in Aveiro and 20 parked vehicles, with batteries of 19, 20 and 21 % around the minimum battery to book (rule BR1) and rare faults. Vehicles move only when the platform unlocks them |
| `ria-ride-charging.yml` | The same fleet with a fast charge rate, to watch the batteries rise to 100 % |
| `ria-ride-trips.yml` | The same fleet riding between hubs on its own, to watch vehicles move on a map: the first one leaves within seconds and reaches another hub in about 2 minutes |
| `ria-ride-faults.yml` | The same fleet with no random faults and three scripted ones (brake wear, a battery overheat that stays, a flat tyre), to test maintenance flows: each appears in the telemetry and as a `FAULT_RAISED` event, and two are cleared with a `FAULT_CLEARED` event |
| `minimal.yml` | One hub and one vehicle, for quick local runs |
| `load-test.yml` | High-rate mode for load tests: the Ria Ride hubs and 1000 generated vehicles, one reading per second each (1000 readings/s) |

## Build and test

Requires Java 21 (the Maven wrapper downloads Maven).

```bash
./mvnw clean verify      # unit and integration tests (need Docker), coverage, formatting
./mvnw test -Pcontract   # contract tests only: every emitted message against contract/schemas
./mvnw spotless:apply    # fix formatting (Google Java Style)
npm ci --prefix contract && npm run validate --prefix contract   # contract schemas and examples
```

## Docker image

The image is public: `ghcr.io/o-reis/tqs-telemetry-simulator`, no login needed.

| Tag | Points to |
| --- | --- |
| `1.0.0` (one per release) | That release; never changes |
| `1.0` | The newest patch release of 1.0 |
| `latest` | The newest build of `main` (each release is also built from `main`) |
| `sha-…` | One commit |

To build it locally: `docker build -t telemetry-simulator .`

## Troubleshooting

| Symptom | Cause and fix |
| --- | --- |
| The log stops at `Started SimulatorApplication` and no message arrives | The simulator cannot reach the broker. It keeps retrying without logging each attempt. Check `SIMULATOR_MQTT_HOST`: inside a container, use the broker's service name (`broker`), not `localhost`. Check that the broker is running: `docker compose ps` |
| `Invalid scenario in ... (N problems):` and the simulator stops | Each line after it gives a field path and the rule it breaks, for example `vehicles[0].battery: must be between 0 and 100 (was 101.0)`. Fix the file and start again |
| `Invalid scenario in file:/config/scenario.yml (1 problem): - file not found or not readable` | The file is not mounted. Put `scenario.yml` next to `docker-compose.yml`, uncomment the `volumes` lines and keep the `file:` prefix in `SIMULATOR_SCENARIO_FILE` |
| `Bind for 0.0.0.0:1883 failed: port is already allocated` | Another broker runs on your machine. Stop it, or change the port mapping to `"1884:1883"` and connect to `localhost:1884` |
| Messages arrive, but not on the topics you subscribed to | Topics use the scenario's `fleetId`: `fleet/{fleetId}/vehicles/{vehicleId}/...`. Subscribe to `fleet/#` to see them all |
| Two simulators on the same broker publish only now and then | Both use the same `SIMULATOR_MQTT_CLIENT_ID`; the broker drops one when the other connects, and they keep taking turns. Give each one its own id, or leave it unset to generate one |
| Telemetry arrives right after you subscribe, older than expected | Telemetry is retained: the broker sends the last message of each vehicle to every new subscriber. Use the message `timestamp` |

## Releases

The simulator has its own version, `MAJOR.MINOR.PATCH` ([semantic versioning](https://semver.org/)),
separate from the contract version. Each release says in [CHANGELOG.md](CHANGELOG.md) which
contract version it implements, so a simulator fix does not need a contract change.

| Simulator change | Version bump |
| --- | --- |
| Implements a new contract MAJOR version, or removes a configuration field or variable | **MAJOR** |
| Implements a new contract MINOR version, or adds a feature or an optional configuration field | **MINOR** |
| Bug fix with no change to behaviour that is documented | **PATCH** |

To release (maintainers):

1. In a pull request, set `<version>` in `pom.xml` to the new version and move the
   `[Unreleased]` entries of `CHANGELOG.md` to a `## [X.Y.Z] - date` section, with the line
   "Implements contract X.Y.Z.". Preview the release notes with
   `.github/scripts/release-notes.sh X.Y.Z`.
2. After the pull request is merged, tag the merge commit on `main` and push the tag:
   `git tag vX.Y.Z && git push origin vX.Y.Z`. A contract release keeps its own tag,
   `contract-vX.Y.Z` (see the contract's change policy).
3. CI checks that the tag matches `pom.xml` and that the changelog entry exists, runs the full
   build, publishes the image with the tags `X.Y.Z`, `X.Y` and `latest`, and creates the GitHub
   release with the changelog entry as its notes. If a check fails, nothing is published: delete the
   tag, fix it in a new pull request and tag again.
4. Announce the release to the other teams, as the contract's change policy asks for contract
   changes.

## Reporting issues and requesting changes

Use [GitHub Issues](https://github.com/o-reis/tqs-telemetry-simulator/issues/new/choose) and pick a
template:

- **Bug report**: the simulator does something different from this README or the contract.
- **Contract change request**: a new or changed topic, message, field or command. It follows the
  contract's [change policy](contract/README.md#versioning-and-change-policy): discussed in the
  issue and announced to every team before it is released.
- **Question**: how to run, configure or integrate with the simulator.

Give the image tag you run and your scenario, so we can reproduce the problem. Never paste
passwords, tokens or personal data.