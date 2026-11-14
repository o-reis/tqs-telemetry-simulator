# Changelog

All notable changes to the contract are recorded here.
The format follows [Keep a Changelog](https://keepachangelog.com/), and the version numbers follow
[semantic versioning](https://semver.org/) as described in the
[change policy](README.md#versioning-and-change-policy).

## 1.0.0

First version of the contract.

- Topics for telemetry, events, commands (`lock`, `unlock`, `ping`) and responses.
- Message envelope with `schemaVersion`, `messageId`, `type`, `timestamp`, `fleetId`, `vehicleId`
  and `data`.
- JSON Schemas (draft 2020-12) for the envelope, telemetry, event, command and response.
- Valid and invalid example messages, checked by `npm run validate`.
- Versioning and change policy.
