---
phase: 02-protocol-network-hardening
plan: 01
subsystem: auth
tags: [websocket, rate-limiting, binary-protocol, rust, axum, tokio]

requires:
  - phase: 01-quick-wins
    provides: RateLimiter struct with check/record_failure/reset, AppState with rate_limiter field, main.rs with into_make_service_with_connect_info

provides:
  - Frame::Auth variant at type byte 0x05 with encode/decode
  - ws_handler rate-limits by IP before WebSocket upgrade using ConnectInfo<SocketAddr>
  - handle_socket validates auth via first binary Frame::Auth with 10-second timeout
  - Constant-time token comparison on first frame
  - Rejected clients receive SessionEvent "unauthorized" frame then connection closes
  - Integration test stubs in ws_auth_test.rs (ignored, awaiting full impl)

affects: [03-tls-cert-management, android-terminal-socket]

tech-stack:
  added: [tokio::time::timeout, ConnectInfo extractor]
  patterns:
    - First-frame binary auth handshake (Frame::Auth 0x05 before PTY setup)
    - Pre-upgrade IP rate limiting via ConnectInfo in WebSocket handlers
    - Token never in URL — moved to binary frame payload

key-files:
  created:
    - backend/tests/ws_auth_test.rs
  modified:
    - backend/src/protocol.rs
    - backend/src/routes/terminal.rs

key-decisions:
  - "Auth frame uses first-frame binary protocol (0x05) — token never appears in WS URL, HTTP logs, or proxy captures"
  - "Rate limiting happens before WebSocket upgrade in ws_handler, not inside handle_socket"
  - "record_failure called on auth rejection, reset called on success — tracks per-IP failure count"
  - "10-second first-frame timeout — any other outcome (timeout, wrong frame, wrong token) closes connection"

patterns-established:
  - "WebSocket auth: ConnectInfo rate check -> upgrade -> first-frame Frame::Auth -> constant-time compare"
  - "TDD for protocol types: write failing test first, add TYPE_ constant + enum variant + encode/decode arms, verify GREEN"

requirements-completed: [TOK-01, TOK-02, NET-02]

duration: 4min
completed: 2026-04-01
---

# Phase 02 Plan 01: WebSocket First-Frame Auth and Rate Limiting Summary

**WebSocket auth migrated from URL query parameter to first binary Frame::Auth (0x05) with pre-upgrade IP rate limiting via ConnectInfo**

## Performance

- **Duration:** 4 min
- **Started:** 2026-04-01T12:06:45Z
- **Completed:** 2026-04-01T12:10:45Z
- **Tasks:** 3
- **Files modified:** 3

## Accomplishments
- `Frame::Auth(Vec<u8>)` variant added at type byte 0x05 with roundtrip encode/decode tests passing
- `ws_handler` now extracts `ConnectInfo<SocketAddr>` and calls `rate_limiter.check(ip)` before any WebSocket upgrade — connection floods are rejected pre-handshake
- `handle_socket` reads first frame with 10-second timeout, validates only `Frame::Auth`, uses constant-time comparison, sends `SessionEvent "unauthorized"` and closes on any failure
- Auth token completely removed from WebSocket URL — no more `?token=` in HTTP logs, proxy logs, or Wireshark captures
- Integration test stubs in `ws_auth_test.rs` (4 ignored tests) ready for full implementation

## Task Commits

Each task was committed atomically:

1. **Task 1: Add Frame::Auth variant to protocol.rs** - `bb4eaed` (feat)
2. **Task 2: Create ws_auth integration test stubs** - `06353dd` (test)
3. **Task 3: Rewrite ws_handler for rate limiting + first-frame auth handshake** - `1ff39ac` (feat)

**Plan metadata:** (docs commit — see below)

_Note: Task 1 used TDD: test written first (RED), Frame::Auth implementation added (GREEN)_

## Files Created/Modified
- `backend/src/protocol.rs` - Added TYPE_AUTH constant, Frame::Auth variant, encode/decode arms, 3 new tests
- `backend/tests/ws_auth_test.rs` - New file: 4 ignored integration test stubs for WS auth handshake
- `backend/src/routes/terminal.rs` - Removed WsQuery, added ConnectInfo+rate limiting in ws_handler, added first-frame auth handshake in handle_socket

## Decisions Made
- Auth frame at type byte 0x05 — next in sequence after TYPE_SESSION_EVENT (0x04), no gaps
- Rate limiting in `ws_handler` before upgrade (not `handle_socket`) — rejects floods before TCP promotion to WebSocket
- `record_failure(ip)` on auth rejection inside `handle_socket` — tracks repeated failures post-upgrade
- 10-second timeout covers both slow senders and missing frames identically

## Deviations from Plan
None - plan executed exactly as written.

## Issues Encountered
A linter/tool reverted the terminal.rs Write operation after the initial write. The changes were re-applied using the Edit tool against the confirmed-read file content, which succeeded. No logic was lost.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Backend WebSocket auth protocol is hardened — ready for Phase 2 Plans 02 and 03 (CORS, TLS)
- Android client (TerminalSocket.kt) must be updated to send Frame.Auth as first binary frame instead of `?token=` URL parameter — this was handled in plan 02-02
- Integration test stubs in `ws_auth_test.rs` need full implementation once an in-process test server is available

---
*Phase: 02-protocol-network-hardening*
*Completed: 2026-04-01*

## Self-Check: PASSED

- backend/src/protocol.rs: FOUND
- backend/tests/ws_auth_test.rs: FOUND
- backend/src/routes/terminal.rs: FOUND
- 02-01-SUMMARY.md: FOUND
- Commit bb4eaed (Frame::Auth variant): FOUND
- Commit 06353dd (ws_auth test stubs): FOUND
- Commit 1ff39ac (ws_handler rewrite): FOUND
