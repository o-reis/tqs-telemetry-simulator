# Changelog

All notable changes to the simulator are recorded here. Changes to the message contract itself are
in [contract/CHANGELOG.md](contract/CHANGELOG.md).
The format follows [Keep a Changelog](https://keepachangelog.com/), and the version numbers follow
[semantic versioning](https://semver.org/). The simulator has its own version; each release says
which contract version it implements (see [Releases](README.md#releases)).

## [Unreleased]

## [1.0.0] - 2026-11-18

Implements contract 1.0.0.

### Added

- Telemetry published over MQTT (retained, QoS 1) on `fleet/{fleetId}/vehicles/{vehicleId}/telemetry`.
- Scenario file (YAML) with hubs, vehicles and simulation parameters, validated at start-up.
- Simulation engine with a configurable emission interval and a seed for reproducible runs.
- Battery model: drain while riding, charge while parked at a hub.
- Trips between hubs, started automatically or by an `unlock` command.
- `lock`, `unlock` and `ping` commands, answered once each on the `responses` topic.
- Fault events (`FAULT_RAISED`, `FAULT_CLEARED`), random or scripted in the scenario.
- High-rate mode for load tests (`load-test.yml`, 1000 readings/s) with the effective rate in the log.
- Contract tests: every emitted message is validated against `contract/schemas`.
- Docker Compose example with an MQTT broker, and a commented scenario template, in `examples/`.
- Versioned Docker images and GitHub releases created from `v*` tags.
- Issue templates for bug reports, contract change requests and questions.
