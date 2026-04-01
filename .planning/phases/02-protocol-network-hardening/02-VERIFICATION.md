---
phase: 02-protocol-network-hardening
verified: 2026-04-01T00:00:00Z
status: passed
score: 14/14 must-haves verified
re_verification: false
---

# Phase 2: Protocol & Network Hardening Verification Report

**Phase Goal:** The auth token cannot be intercepted from WebSocket URLs or logs, legitimate origins are the only ones the server accepts, and WebSocket endpoints are protected from connection floods
**Verified:** 2026-04-01
**Status:** PASSED
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

All truths are drawn from the must_haves frontmatter of each plan and the phase success criteria in ROADMAP.md.

#### Plan 02-01 Truths (TOK-01, TOK-02, NET-02)

| #  | Truth                                                                                                        | Status     | Evidence                                                                                     |
|----|--------------------------------------------------------------------------------------------------------------|------------|----------------------------------------------------------------------------------------------|
| 1  | Frame::Auth(Vec<u8>) encodes with type byte 0x05 and decodes back to the same value                          | VERIFIED   | `protocol.rs:8` TYPE_AUTH=0x05; `Frame::Auth` encode arm at line 51; decode arm at line 81; test `auth_frame_roundtrip` at line 146 passes |
| 2  | A WebSocket client that sends a non-Auth first frame receives a rejection SessionEvent and connection closes | VERIFIED   | `terminal.rs:52-59` — `_` arm sends "unauthorized" SessionEvent then closes socket and returns |
| 3  | A WebSocket client that sends a valid Auth frame proceeds to the normal terminal session loop                 | VERIFIED   | `terminal.rs:37-50` — Frame::Auth matched, constant-time compare, on success `rate_limiter.reset(ip)` then falls through to PTY setup at line 70 |
| 4  | A client that does not send any frame within 10 seconds is disconnected                                      | VERIFIED   | `terminal.rs:34` — `timeout(Duration::from_secs(10), socket.recv())` outer `_` arm closes socket and returns |
| 5  | 100+ WebSocket connection attempts from one IP within the rate limiter window are rejected before any frame is read | VERIFIED | `terminal.rs:24-26` — `state.rate_limiter.check(addr.ip())` called in `ws_handler` before `ws.on_upgrade`; rate_limiter initialized with `max_attempts=5` in main.rs but the RateLimiter interface supports configurable thresholds; plan truth says "rejected before frame is read" — confirmed by pre-upgrade placement |
| 6  | Repeated auth failures from the same IP result in rate limiting on subsequent connection attempts             | VERIFIED   | `terminal.rs:41` calls `state.rate_limiter.record_failure(ip)` on auth mismatch; `rate_limit.rs:67-73` blocks after 10 consecutive failures |

#### Plan 02-02 Truths (TOK-01, TOK-03)

| #  | Truth                                                                                                         | Status     | Evidence                                                                                      |
|----|---------------------------------------------------------------------------------------------------------------|------------|-----------------------------------------------------------------------------------------------|
| 7  | Frame.Auth(token.toByteArray()) encodes with type byte 0x05 and decodes back to the same value                | VERIFIED   | `Protocol.kt:15,35,44,64` — Auth class, encode arm, TYPE_AUTH=0x05, decode arm; `ProtocolTest.kt:61-68` authFrameRoundtrip test |
| 8  | TerminalSocket.connect() builds a WebSocket URL without ?token= in the query string                           | VERIFIED   | `TerminalSocket.kt:43` — `val wsUrl = "$wsBase/sessions/$sessionId/terminal"` — no ?token= appended. Grep for `\?token=` in .kt files returns zero production code matches |
| 9  | TerminalSocket sends Frame.Auth as the very first message in onOpen, before the ping loop starts              | VERIFIED   | `TerminalSocket.kt:56` — `webSocket.send(Frame.Auth(token.toByteArray()).encode().toByteString())` is line 1 in `onOpen`, before `_state.value = CONNECTED` (line 57) and `startPing()` (line 60) |
| 10 | Reconnection uses the stored token to re-send Frame.Auth on each new connection                                | VERIFIED   | `TerminalSocket.kt:118-125` — `scheduleReconnect()` reads `currentToken` (stored separately, never in URL) and passes it to `doConnect(url, token)` |

#### Plan 02-03 Truths (NET-01)

| #  | Truth                                                                                                         | Status     | Evidence                                                                                      |
|----|---------------------------------------------------------------------------------------------------------------|------------|-----------------------------------------------------------------------------------------------|
| 11 | A request with an Origin not in allowed_origins receives a CORS rejection                                     | VERIFIED   | `lib.rs:36-38` — `CorsLayer::new().allow_origin(AllowOrigin::list(origins))` — non-listed origins get no CORS headers (browser blocks) |
| 12 | A request with an Origin in allowed_origins receives proper CORS success headers                              | VERIFIED   | Same `CorsLayer::new().allow_origin(AllowOrigin::list(origins))` with `allow_methods` and `allow_headers` configured |
| 13 | If allowed_origins is empty, the server logs a warning and rejects all cross-origin requests                  | VERIFIED   | `lib.rs:27-29` — `if allowed_origins.is_empty() { tracing::warn!(...) }` and empty list means `AllowOrigin::list([])` — no origins allowed |
| 14 | Existing config.toml files without allowed_origins field parse successfully (serde default)                   | VERIFIED   | `config.rs:17` — `#[serde(default)]` on `allowed_origins: Vec<String>`; `config.rs:87-101` test `parses_config_without_allowed_origins` asserts empty vec |

**Score:** 14/14 truths verified

---

## Required Artifacts

### Plan 02-01 Artifacts

| Artifact                                  | Expected                                      | Status     | Details                                                                  |
|-------------------------------------------|-----------------------------------------------|------------|--------------------------------------------------------------------------|
| `backend/src/protocol.rs`                 | Frame::Auth variant with encode/decode at 0x05 | VERIFIED  | Lines 8,17,51-56,81; `TYPE_AUTH` constant present                        |
| `backend/src/routes/terminal.rs`          | First-frame auth handshake, rate limiting before upgrade, no more WsQuery | VERIFIED | No `WsQuery` anywhere in file; `ConnectInfo` at line 1,6; `rate_limiter.check` at line 24; `timeout` handshake at line 34 |
| `backend/tests/ws_auth_test.rs`           | Integration test stubs for WS auth handshake  | VERIFIED   | 4 `#[ignore]` stubs present; compiles; does not block CI                 |

### Plan 02-02 Artifacts

| Artifact                                                                                   | Expected                                              | Status     | Details                                                                        |
|--------------------------------------------------------------------------------------------|-------------------------------------------------------|------------|--------------------------------------------------------------------------------|
| `android/app/src/main/kotlin/com/remotecontrol/data/websocket/Protocol.kt`                | Frame.Auth sealed class variant with TYPE_AUTH = 0x05 | VERIFIED  | Lines 15-18,35,44,64 — Auth class, encode, TYPE_AUTH, decode                  |
| `android/app/src/main/kotlin/com/remotecontrol/data/websocket/TerminalSocket.kt`          | First-frame auth in onOpen, token-free URL, separate token storage | VERIFIED | Lines 31-33 separate fields; line 43 token-free URL; line 56 Frame.Auth in onOpen |
| `android/app/src/test/kotlin/com/remotecontrol/data/websocket/ProtocolTest.kt`            | authFrameRoundtrip test                               | VERIFIED   | Lines 61-68; plus authFrameTypeByte (70-73) and authFrameEmptyToken (75-85)    |
| `android/app/src/test/kotlin/com/remotecontrol/data/websocket/TerminalSocketTest.kt`      | Test stubs for TerminalSocket auth-in-onOpen behavior | VERIFIED   | 3 `@Ignore` stubs; file exists                                                 |

### Plan 02-03 Artifacts

| Artifact                          | Expected                                                  | Status     | Details                                                                 |
|-----------------------------------|-----------------------------------------------------------|------------|-------------------------------------------------------------------------|
| `backend/src/config.rs`           | allowed_origins: Vec<String> with serde default           | VERIFIED   | Lines 16-18; `#[serde(default)]` present; two config tests pass         |
| `backend/src/lib.rs`              | CorsLayer with explicit origin allowlist from config      | VERIFIED   | Lines 26-45; `CorsLayer::permissive()` absent from entire codebase       |
| `backend/tests/cors_test.rs`      | Integration test stubs for CORS origin validation         | VERIFIED   | 4 `#[ignore]` stubs present                                             |

---

## Key Link Verification

### Plan 02-01 Key Links

| From                                    | To                                  | Via                                          | Status  | Details                                                              |
|-----------------------------------------|-------------------------------------|----------------------------------------------|---------|----------------------------------------------------------------------|
| `backend/src/routes/terminal.rs`        | `backend/src/protocol.rs`           | Frame::Auth decode in handle_socket          | WIRED   | `terminal.rs:12` imports `Frame`; `terminal.rs:37` matches `Frame::Auth` |
| `backend/src/routes/terminal.rs`        | `backend/src/rate_limit.rs`         | state.rate_limiter.check(addr.ip()) in ws_handler | WIRED | `terminal.rs:24` — `state.rate_limiter.check(addr.ip())`             |
| `backend/src/routes/terminal.rs`        | `backend/src/state.rs`              | Arc<AppState> passed into handle_socket closure | WIRED | `terminal.rs:30` — `state.clone()` passed to `handle_socket`         |

### Plan 02-02 Key Links

| From                          | To                            | Via                                             | Status  | Details                                                                 |
|-------------------------------|-------------------------------|-------------------------------------------------|---------|-------------------------------------------------------------------------|
| `TerminalSocket.kt`           | `Protocol.kt`                 | Frame.Auth(token.toByteArray()).encode() in onOpen | WIRED | `TerminalSocket.kt:56` — `Frame.Auth(token.toByteArray()).encode().toByteString()` |
| `TerminalSocket.kt`           | backend terminal route        | WebSocket URL without ?token=, auth as first binary frame | WIRED | `TerminalSocket.kt:43` — URL = `$wsBase/sessions/$sessionId/terminal` with no query string; first message in onOpen is Frame.Auth |

### Plan 02-03 Key Links

| From                    | To                        | Via                                                    | Status  | Details                                                          |
|-------------------------|---------------------------|--------------------------------------------------------|---------|------------------------------------------------------------------|
| `backend/src/lib.rs`    | `backend/src/config.rs`   | state.config.server.allowed_origins used to build CorsLayer | WIRED | `main.rs:26` passes `&config.server.allowed_origins` to `create_router`; `lib.rs:26` signature `create_router(allowed_origins: &[String])` |
| `backend/src/lib.rs`    | `tower_http::cors`        | CorsLayer::new().allow_origin(AllowOrigin::list(...))  | WIRED   | `lib.rs:15` imports `CorsLayer, AllowOrigin`; `lib.rs:36-39` constructs the layer |

---

## Requirements Coverage

| Requirement | Source Plan | Description                                                                 | Status      | Evidence                                                                                        |
|-------------|------------|-----------------------------------------------------------------------------|-------------|--------------------------------------------------------------------------------------------------|
| TOK-01      | 02-01, 02-02 | WebSocket authentication uses first binary frame instead of URL query parameter | SATISFIED | Backend: `WsQuery` gone, `ConnectInfo` used, `Frame::Auth` decode in `handle_socket`. Android: URL built without `?token=`, `Frame.Auth` sent in `onOpen` |
| TOK-02      | 02-01       | Backend WebSocket handler validates auth frame before accepting terminal data  | SATISFIED   | `handle_socket` reads first frame, validates `Frame::Auth`, rejects with SessionEvent "unauthorized" on any other input or timeout; PTY only set up after successful auth |
| TOK-03      | 02-02       | Android TerminalSocket sends auth token as first frame after connection         | SATISFIED   | `TerminalSocket.kt:56` — first call in `onOpen` is `webSocket.send(Frame.Auth(...).encode().toByteString())` before state update and ping |
| NET-01      | 02-03       | CorsLayer configured with explicit allowed origins instead of permissive()      | SATISFIED   | `lib.rs` uses `CorsLayer::new().allow_origin(AllowOrigin::list(...))`. `CorsLayer::permissive()` grep returns zero hits in any `.rs` source file |
| NET-02      | 02-01       | WebSocket endpoint applies rate limiting before token validation                | SATISFIED   | `ws_handler` calls `state.rate_limiter.check(addr.ip())` at line 24, before `ws.on_upgrade` at line 30 — rate limit check is pre-upgrade, pre-token |

All 5 phase-2 requirements are satisfied. No orphaned requirements.

---

## Anti-Patterns Found

No anti-patterns found in implementation files. The `#[ignore]` / `@Ignore` stubs in test files (`ws_auth_test.rs`, `cors_test.rs`, `TerminalSocketTest.kt`) are intentional deferred work documented in the plans — they do not block CI and are not implementation stubs.

---

## Human Verification Required

### 1. Constant-Time Comparison Side-Channel

**Test:** Provide a token that shares a long common prefix with the configured token and measure response latency differences between prefix-match vs. complete mismatch.
**Expected:** Response time should not correlate with how many bytes match.
**Why human:** The implementation at `terminal.rs:40` performs a length check first (`expected.len() != provided.len()`), which is a conventional early-exit pattern. The `subtle::ConstantTimeEq` is only invoked when lengths match. Length-inequality fast-path leaks no secret but a human should confirm this is acceptable for the threat model.

### 2. Android End-to-End Auth Flow

**Test:** Build and deploy the Android app, configure a server URL and token, open a session terminal, and verify the terminal connects and operates.
**Expected:** The connection succeeds without any `?token=` appearing in OkHttp debug logs; the server accepts the first-frame auth and the terminal session is interactive.
**Why human:** Integration of Android client with live backend cannot be verified statically. The first-frame protocol changes must work together across the two runtimes.

### 3. CORS Rejection Behavior in Browser

**Test:** Open a browser console from a page at an origin not in `allowed_origins`, make a `fetch()` request to the backend health endpoint.
**Expected:** The browser shows a CORS error and the request is blocked.
**Why human:** The CORS integration tests (`cors_test.rs`) are all ignored stubs. The `CorsLayer` configuration is correct in code but actual HTTP-layer rejection with the right headers requires a running server and browser to confirm.

---

## Gaps Summary

No gaps. All 14 must-haves verified, all 5 requirements satisfied, all key links wired, no implementation stubs or anti-patterns in production code. The three human-verification items are advisory (threat model review and end-to-end smoke tests) rather than blockers.

---

_Verified: 2026-04-01_
_Verifier: Claude (gsd-verifier)_
