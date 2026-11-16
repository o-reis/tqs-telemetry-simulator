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

If the broker is unreachable or the connection drops, the simulator keeps retrying (from 1 s up to
2 min between attempts) and resends what it could not deliver.

## How telemetry is published

Each vehicle state becomes a contract `telemetry` message (envelope with `schemaVersion`, a new
`messageId`, a UTC `timestamp`, `fleetId` and `vehicleId`), published as **retained, QoS 1** on
`fleet/{fleetId}/vehicles/{vehicleId}/telemetry`. The tests validate every built message against
the JSON Schemas in `contract/schemas`, and an integration test reads it back from a real Mosquitto
broker.

## Build and test

Requires Java 21 (the Maven wrapper downloads Maven).

```bash
./mvnw clean verify      # unit and integration tests (need Docker), coverage, formatting
./mvnw spotless:apply    # fix formatting (Google Java Style)
npm ci --prefix contract && npm run validate --prefix contract   # contract schemas and examples
```

## Docker image

Every merge to `main` publishes `ghcr.io/o-reis/tqs-telemetry-simulator` with the tags `latest`
and the commit SHA. To build it locally: `docker build -t telemetry-simulator .`
