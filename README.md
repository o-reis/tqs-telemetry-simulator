# Fleet telemetry simulator

A separate service that simulates a fleet of light vehicles and publishes their telemetry over MQTT,
following the interface contract in [contract/](contract/README.md). Any platform that implements the
contract can consume it.

## Configuration

The broker connection is set with environment variables:

| Variable | Default | Meaning |
| --- | --- | --- |
| `SIMULATOR_MQTT_HOST` | `localhost` | Broker host |
| `SIMULATOR_MQTT_PORT` | `1883` | Broker port |
| `SIMULATOR_MQTT_USERNAME` | empty | Username; empty connects without authentication |
| `SIMULATOR_MQTT_PASSWORD` | empty | Password, used with the username |
| `SIMULATOR_MQTT_CLIENT_ID` | generated | MQTT client id; must be unique per broker |
| `SIMULATOR_SCENARIO_FILE` | `classpath:scenarios/ria-ride.yml` | Scenario file (hubs, vehicles, simulation parameters). Use `file:/config/scenario.yml` for a file mounted into the container |

If the broker is unreachable or the connection drops, the simulator keeps retrying (from 1 s up to
2 min between attempts) and resends what it could not deliver.

## How telemetry is published

Each vehicle state becomes a contract `telemetry` message (envelope with `schemaVersion`, a new
`messageId`, a UTC `timestamp`, `fleetId` and `vehicleId`), published as **retained, QoS 1** on
`fleet/{fleetId}/vehicles/{vehicleId}/telemetry`. The tests validate every built message against
the JSON Schemas in `contract/schemas`, and an integration test reads it back from a real Mosquitto
broker.

## How the simulation runs

At start-up the simulator begins a run that ticks every `simulation.emissionIntervalSeconds`
(fixed rate, first tick immediately). On each tick every vehicle, in scenario order, publishes one
telemetry message. Vehicles stay parked and locked in their hub (hub position, speed 0, battery from
the scenario). A healthy vehicle gets a fault with `simulation.faultProbability` per tick; once
raised, a fault stays for the rest of the run.

Fault codes this simulator raises (the contract lets each simulator define its own):

| Code | Meaning |
| --- | --- |
| `BRAKE_WEAR` | Brakes need service |
| `FLAT_TIRE` | A tyre is flat |
| `BATTERY_OVERHEAT` | Battery temperature too high |

**Reproducible runs.** All randomness (faults and message ids) comes from one generator seeded with
`simulation.seed`, and all time from one injected clock. Two runs with the same seed and scenario
publish the same sequence of messages; only the timestamps follow the real clock. The engine is
tested without a broker and without real time.

## Scenario file

YAML read at start-up; examples in `src/main/resources/scenarios/`. If the file is missing or
breaks a rule, the simulator does not start and the log lists every problem with its field path.

| Field | Meaning |
| --- | --- |
| `fleetId` | Fleet identifier, used in every topic and message |
| `simulation.emissionIntervalSeconds` | Seconds between readings of a vehicle, greater than 0 |
| `simulation.seed` | Whole number; the same seed gives the same run |
| `simulation.faultProbability` | Chance of a fault per reading, from 0 to 1 |
| `hubs[]` | `id`, `name`, `position` (`lat` -90 to 90, `lon` -180 to 180) |
| `vehicles[]` | `id`, `type` (`E_BIKE` or `E_SCOOTER`), `hub` (an id from `hubs`), `battery` (0 to 100) |

Identifiers match `[A-Za-z0-9_-]{1,64}` and are unique within hubs and within vehicles. The vehicle
type is internal to the simulator and never appears in the contract messages.

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
