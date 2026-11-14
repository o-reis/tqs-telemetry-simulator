# Fleet Telemetry Simulator Contract

**Contract version: 1.0.0** (see [CHANGELOG.md](CHANGELOG.md) and the [change policy](#versioning-and-change-policy))

This contract is the interface between a fleet telemetry simulator and any platform that consumes it.
It lets each team write its own adapter independently, and lets a platform swap one simulator for
another without changing business logic.

The contract is **generic**. It describes light vehicles that report their state and accept
commands. It has no concept from any particular business (no trips, prices, users, bookings or
tourism fields). Those concepts live in the platform, never on the wire.

## Contents

- [Transport](#transport)
- [Identifiers](#identifiers)
- [Topics](#topics)
- [Message envelope](#message-envelope)
- [Message types](#message-types)
- [Commands and responses](#commands-and-responses)
- [Versioning and change policy](#versioning-and-change-policy)
- [Schemas and examples](#schemas-and-examples)

## Transport

- Protocol: MQTT (3.1.1 or 5), any compliant broker. The contract does not depend on a broker product.
- Payload: UTF-8 JSON, one message per MQTT publish.
- Time: every timestamp is UTC, ISO 8601 with the `Z` suffix, e.g. `2026-11-10T10:00:00Z`.
- Units: battery in percent (0 to 100), position as WGS84 decimal degrees, speed in km/h.

## Identifiers

`fleetId`, `vehicleId` and `hubId` are strings matching `^[A-Za-z0-9_-]{1,64}$`.
They never contain `/`, `+` or `#`, so they are always safe inside a topic.

## Topics

`{fleetId}` and `{vehicleId}` are replaced by real identifiers.

| Topic | Direction | Message | QoS | Retained |
| --- | --- | --- | --- | --- |
| `fleet/{fleetId}/vehicles/{vehicleId}/telemetry` | simulator to platform | [telemetry](#telemetry) | 1 | yes |
| `fleet/{fleetId}/vehicles/{vehicleId}/events` | simulator to platform | [event](#event) | 1 | no |
| `fleet/{fleetId}/vehicles/{vehicleId}/commands/lock` | platform to simulator | [lock command](#commands-and-responses) | 1 | no |
| `fleet/{fleetId}/vehicles/{vehicleId}/commands/unlock` | platform to simulator | [unlock command](#commands-and-responses) | 1 | no |
| `fleet/{fleetId}/vehicles/{vehicleId}/commands/ping` | platform to simulator | [ping command](#commands-and-responses) | 1 | no |
| `fleet/{fleetId}/vehicles/{vehicleId}/responses` | simulator to platform | [response](#response) | 1 | no |

Useful subscriptions (`+` matches exactly one level):

| Goal | Subscribe to |
| --- | --- |
| One vehicle's telemetry | `fleet/{fleetId}/vehicles/{vehicleId}/telemetry` |
| Whole fleet's telemetry | `fleet/{fleetId}/vehicles/+/telemetry` |
| Whole fleet's events | `fleet/{fleetId}/vehicles/+/events` |
| All responses of a fleet | `fleet/{fleetId}/vehicles/+/responses` |
| A simulated vehicle's commands | `fleet/{fleetId}/vehicles/{vehicleId}/commands/+` |

Rules:

- **Retained telemetry.** The simulator publishes telemetry as retained, so a platform that
  connects late immediately receives the last known state of each vehicle. A retained message can
  be old: consumers must check the `timestamp` and treat old readings as stale. The contract does
  not fix a telemetry period; the simulator chooses it and documents it.
- **Events and responses are not retained.** They describe something that happened once.
- **Topic and payload agree.** The `fleetId` and `vehicleId` in the topic must equal the ones in the
  payload. A consumer rejects a message where they differ.
- **Major versions.** Version 1 uses the topics above. A breaking change (major version 2 or
  later) prefixes every topic with `v2/`, so old and new consumers can coexist on one broker.

## Message envelope

Every message, in both directions, has the same outer shape. The type-specific content is in `data`.

| Field | Type | Required | Meaning |
| --- | --- | --- | --- |
| `schemaVersion` | string | yes | Contract version the sender follows (semantic version, e.g. `1.0.0`) |
| `messageId` | string (UUID) | yes | Unique id of this message, used to detect duplicates (QoS 1 can deliver twice) |
| `type` | string | yes | `telemetry`, `event`, `command` or `response` |
| `timestamp` | string (UTC) | yes | When the message was created by the sender |
| `fleetId` | string | yes | Fleet the vehicle belongs to |
| `vehicleId` | string | yes | Vehicle the message is about |
| `data` | object | yes | Content, depends on `type` |

## Message types

### Telemetry

The current state of a vehicle. It is state, not history: each message replaces the previous one.

| `data` field | Type | Required | Meaning |
| --- | --- | --- | --- |
| `batteryPercent` | number 0 to 100 | yes | Battery level |
| `lockState` | `locked` or `unlocked` | yes | Whether the vehicle is locked |
| `position` | object or `null` | yes | `{ "lat": number, "lon": number }`, or `null` if unknown |
| `hubId` | string or `null` | yes | Hub where the vehicle is parked, or `null` if it is not in a hub |
| `faultCode` | string or `null` | yes | Current active fault, or `null` if none |
| `speedKmh` | number or absent | no | Current speed |

At least one of `position` and `hubId` must be non-null: a vehicle is either in a known hub or at
a known position (or both).

Example:

```json
{
  "schemaVersion": "1.0.0",
  "messageId": "7d9f2c4e-3b1a-4e55-9a60-0c1f8f6b1a01",
  "type": "telemetry",
  "timestamp": "2026-11-10T10:00:00Z",
  "fleetId": "fleet-01",
  "vehicleId": "bike-0042",
  "data": {
    "batteryPercent": 78.5,
    "lockState": "locked",
    "position": { "lat": 40.6405, "lon": -8.6538 },
    "hubId": "hub-03",
    "faultCode": null,
    "speedKmh": 0
  }
}
```

### Event

Something that happened once. Telemetry carries the **current** `faultCode`; an event reports the
**moment** it appeared or went away. Keep both: if one telemetry message is lost, the fault is still
reported by the next one, and the event gives the exact time.

| `data` field | Type | Required | Meaning |
| --- | --- | --- | --- |
| `eventType` | string | yes | `FAULT_RAISED`, `FAULT_CLEARED` or `LOCK_CHANGED` |
| `severity` | string | yes | `INFO`, `WARNING` or `CRITICAL` |
| `faultCode` | string or `null` | yes | The fault concerned, `null` for `LOCK_CHANGED` |
| `lockState` | `locked`, `unlocked` or `null` | yes | New lock state for `LOCK_CHANGED`, otherwise `null` |
| `description` | string | no | Short human-readable text, never needed to process the event |

Example:

```json
{
  "schemaVersion": "1.0.0",
  "messageId": "0b8e5a62-5d6e-4c0a-8d11-2f6c0b9f4a02",
  "type": "event",
  "timestamp": "2026-11-10T10:05:12Z",
  "fleetId": "fleet-01",
  "vehicleId": "bike-0042",
  "data": {
    "eventType": "FAULT_RAISED",
    "severity": "WARNING",
    "faultCode": "BRAKE_WEAR",
    "lockState": null,
    "description": "Rear brake pad below threshold"
  }
}
```

Fault codes are plain strings in `UPPER_SNAKE_CASE`. The contract does not list them: a simulator
defines its own and documents them, and a platform treats an unknown code as a generic fault.

### Response

See [Commands and responses](#commands-and-responses).

## Commands and responses

The platform sends a command to the vehicle's command topic. The simulator answers on the
`responses` topic of the same vehicle. MQTT has no built-in reply, so the pair is linked by
`commandId`.

Command `data` fields:

| `data` field | Type | Required | Meaning |
| --- | --- | --- | --- |
| `commandId` | string (UUID) | yes | Chosen by the platform, echoed in the response |
| `expiresAt` | string (UTC) or absent | no | If the simulator receives the command after this time, it must reject it |

The command name comes from the topic (`lock`, `unlock`, `ping`) and is also repeated in the response.

Commands in version 1:

| Command | Effect |
| --- | --- |
| `lock` | Lock the vehicle |
| `unlock` | Unlock the vehicle |
| `ping` | Do nothing, only answer. Lets a platform check that the vehicle is reachable |

Commands are **idempotent**: sending `lock` to an already locked vehicle changes nothing and is
answered `COMPLETED`. This makes a retry after a timeout safe.

Response `data` fields:

| `data` field | Type | Required | Meaning |
| --- | --- | --- | --- |
| `commandId` | string (UUID) | yes | The command being answered |
| `command` | `lock`, `unlock` or `ping` | yes | The command being answered |
| `status` | string | yes | `COMPLETED`, `REJECTED` or `FAILED` |
| `reason` | string or `null` | yes | Why it was not `COMPLETED`, `null` otherwise |

Meaning of the statuses:

- `COMPLETED`: the command was executed (or was already true, see idempotent).
- `REJECTED`: the simulator refused it by rule, e.g. the command expired, or the vehicle is unknown.
- `FAILED`: the simulator tried and could not (e.g. a simulated hardware fault).

Reasons are plain `UPPER_SNAKE_CASE` strings such as `EXPIRED` or `UNKNOWN_VEHICLE`.

**Timeout.** If the platform receives no response, it decides what to do. The contract recommends
waiting 10 seconds, then treating the command as failed. Since commands are idempotent, the
platform may then send the same command again with a **new** `commandId`.

Example, command `unlock` on `fleet/fleet-01/vehicles/bike-0042/commands/unlock`:

```json
{
  "schemaVersion": "1.0.0",
  "messageId": "c3a1f8d0-6a24-4f67-b1d2-9e3e4a5b6c03",
  "type": "command",
  "timestamp": "2026-11-10T10:10:00Z",
  "fleetId": "fleet-01",
  "vehicleId": "bike-0042",
  "data": {
    "commandId": "e5b2a9c1-0d3f-4a8e-8b77-1c2d3e4f5a04",
    "expiresAt": "2026-11-10T10:10:30Z"
  }
}
```

Example response on `fleet/fleet-01/vehicles/bike-0042/responses`:

```json
{
  "schemaVersion": "1.0.0",
  "messageId": "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c05",
  "type": "response",
  "timestamp": "2026-11-10T10:10:01Z",
  "fleetId": "fleet-01",
  "vehicleId": "bike-0042",
  "data": {
    "commandId": "e5b2a9c1-0d3f-4a8e-8b77-1c2d3e4f5a04",
    "command": "unlock",
    "status": "COMPLETED",
    "reason": null
  }
}
```

## Versioning and change policy

The contract follows [semantic versioning](https://semver.org/): `MAJOR.MINOR.PATCH`.

| Change | Version bump |
| --- | --- |
| Remove or rename a field, change its type or meaning, make an optional field required, add a required field, change a topic or QoS rule, remove an enum value or a command | **MAJOR** |
| Add an optional field, a new enum value for events, or a new command | **MINOR** |
| Fix wording, examples or documentation without changing any rule | **PATCH** |

Rules:

1. Every message carries `schemaVersion`. The MAJOR part tells a consumer which rules apply.
2. **Tolerant reader.** A consumer ignores fields and event types it does not know, and uses
   `schemaVersion` only to select rules by MAJOR. This is what makes MINOR changes safe.
3. A MAJOR change uses the `v{MAJOR}/` topic prefix (see [Topics](#topics)). The previous
   major version stays supported by the simulator for at least one announced transition period.
4. Every change goes through a pull request that updates the schemas, the examples,
   [CHANGELOG.md](CHANGELOG.md) and the version number together. Each release is a git tag
   `contract-vMAJOR.MINOR.PATCH`.
5. A change is announced to the teaching staff and the other teams before it is released.

## Schemas and examples

- JSON Schemas (draft 2020-12) are in [schemas/](schemas/). They are deliberately **open**: they
  do not forbid unknown fields, so a message from a newer MINOR version still validates against an
  older schema (the tolerant reader rule). The cost is that a misspelled optional field is not
  caught by the schema.
- Valid and invalid example messages are in [examples/](examples/). Every valid example must
  validate against its schema and every invalid example must be rejected. See
  [validation](validation.md).
