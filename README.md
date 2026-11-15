# Fleet telemetry simulator

A separate service that simulates a fleet of light vehicles and publishes their telemetry over MQTT,
following the interface contract in [contract/](contract/README.md). Any platform that implements the
contract can consume it.

Part of the TQS group P11 project (Universidade de Aveiro, 2026); the platform lives in
[Ria-Ride/TQS-Group-P11](https://github.com/Ria-Ride/TQS-Group-P11).

## Build and test

Requires Java 21 (the Maven wrapper downloads Maven).

```bash
./mvnw clean verify      # unit and integration tests, coverage check, formatting check
./mvnw spotless:apply    # fix formatting (Google Java Style)
npm ci --prefix contract && npm run validate --prefix contract   # contract schemas and examples
```

## Docker image

Every merge to `main` publishes `ghcr.io/o-reis/tqs-telemetry-simulator` with the tags `latest`
and the commit SHA. To build it locally: `docker build -t telemetry-simulator .`
