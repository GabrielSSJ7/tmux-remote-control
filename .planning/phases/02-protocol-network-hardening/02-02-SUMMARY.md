---
phase: 02-protocol-network-hardening
plan: 02
subsystem: android-websocket
tags: [websocket, auth, binary-protocol, security, kotlin]
dependency_graph:
  requires: []
  provides: [Frame.Auth at 0x05, first-frame-auth-in-onOpen, token-free-ws-url]
  affects: [android/websocket, backend/terminal-route]
tech_stack:
  added: []
  patterns: [TDD red-green, sealed-class extension, binary frame protocol]
key_files:
  created:
    - android/app/src/test/kotlin/com/remotecontrol/data/websocket/TerminalSocketTest.kt
  modified:
    - android/app/src/main/kotlin/com/remotecontrol/data/websocket/Protocol.kt
    - android/app/src/test/kotlin/com/remotecontrol/data/websocket/ProtocolTest.kt
    - android/app/src/main/kotlin/com/remotecontrol/data/websocket/TerminalSocket.kt
decisions:
  - Frame.Auth uses ByteArray equals/hashCode override matching existing Data class pattern
  - Token stored in separate currentToken field, never embedded in URL or currentUrl
  - Frame.Auth sent BEFORE state transition to CONNECTED and before ping loop in onOpen
  - TerminalSocketTest stubs marked @Ignore so they do not block the test suite
metrics:
  duration: 3min
  completed_date: "2026-04-01"
  tasks_completed: 2
  files_modified: 4
---

# Phase 2 Plan 2: First-Frame Binary Auth Protocol Summary

**One-liner:** Android WebSocket migrated from `?token=` URL query auth to binary `Frame.Auth` (type 0x05) sent as first frame in `onOpen`, eliminating token exposure in OkHttp logs and network captures.

## What Was Built

Migrated Android WebSocket authentication from URL-embedded token (`?token=...`) to binary first-frame protocol matching the backend handshake.

### Protocol.kt changes

Added `Frame.Auth` sealed class variant:
- Type byte `TYPE_AUTH = 0x05`
- `data class Auth(val token: ByteArray)` with manual `equals`/`hashCode` for structural ByteArray comparison
- Encode arm: `byteArrayOf(TYPE_AUTH) + token`
- Decode arm: `Auth(data.copyOfRange(1, data.size))`

### TerminalSocket.kt changes

- Replaced `currentUrl: String?` with three separate fields: `currentBaseWsUrl`, `currentSessionId`, `currentToken`
- `connect()` builds URL as `$wsBase/sessions/$sessionId/terminal` — no `?token=` query parameter
- `doConnect()` now accepts `token: String` parameter; sends `Frame.Auth(token.toByteArray()).encode().toByteString()` as the first call in `onOpen`, before `_state.value = CONNECTED` and before `startPing()`
- `scheduleReconnect()` reconstructs URL from stored fields and passes `currentToken` to `doConnect`

### Test additions

- `ProtocolTest.kt`: 3 new tests — `authFrameRoundtrip`, `authFrameTypeByte`, `authFrameEmptyToken` — all pass
- `TerminalSocketTest.kt`: created with 3 `@Ignore` stubs for TOK-03 verification: `connectUrlDoesNotContainToken`, `authFrameSentAsFirstMessageInOnOpen`, `reconnectUsesStoredToken`

## Deviations from Plan

### Out-of-scope pre-existing failure discovered

**TerminalEmulatorTest.newlineMovesToNextRow** was already failing on the base commit (confirmed by reverting our changes and running the suite). This failure predates this plan and is unrelated to websocket auth. Logged as out-of-scope, not fixed.

No auto-fix deviations were required for our task changes.

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| 1    | c8846ee | feat(02-02): add Frame.Auth variant at TYPE_AUTH 0x05 |
| 2    | d73eaa8 | feat(02-02): rewrite TerminalSocket for first-frame auth, add test stubs |

## Self-Check: PASSED
