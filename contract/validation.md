# Validating the contract

## Run the checks

You need Node.js (tested with v24). From the `contract/` folder:

```bash
npm ci
npm run validate
```

The script (`validate.mjs`) checks that:

- every file in `examples/valid/` validates against its schema;
- every file in `examples/invalid/` is **rejected** by its schema;
- every schema (except the envelope) has at least one valid example.

It exits with a non-zero code if any check fails, so it can run in CI.

### Which schema checks an example

The part of the file name before the first `-` selects the schema:

| File name starts with | Schema |
| --- | --- |
| `telemetry-` | `schemas/telemetry.schema.json` |
| `event-` | `schemas/event.schema.json` |
| `command-` | `schemas/command.schema.json` |
| `response-` | `schemas/response.schema.json` |

Name an invalid example after its single defect (e.g. `telemetry-battery-over-100.json`), so a
failure points at exactly one rule. An invalid example should be a valid one with **one** change.

## What the schemas cannot check

These rules are in the [README](README.md) and must be checked by the adapter code and its tests:

- The `fleetId` and `vehicleId` in the topic equal the ones in the payload.
- The command name in the topic matches the `command` in its response.
- A retained telemetry message may be old: use the `timestamp` to detect stale data.

## Review checklist for a contract change

The contract must stay independent of any specific application. Before a change is merged, a
reviewer checks by hand:

- [ ] No field, enum value, example or text uses a concept from one specific business (for example
      trips, bookings, prices, passes, users or tourism). Those belong to the platform, not the wire.
- [ ] Example names and ids are fictional and neutral (`fleet-01`, `hub-03`).
- [ ] The change is classified (major, minor or patch) following the change policy, and the
      version in `README.md`, `package.json` and `CHANGELOG.md` is updated to match.
- [ ] Schemas, examples and the README table for the message agree with each other.
- [ ] `npm run validate` passes.
