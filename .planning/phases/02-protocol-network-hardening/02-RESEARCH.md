# Phase 2: Protocol & Network Hardening - Research

**Researched:** 2026-03-31
**Domain:** Axum WebSocket auth migration, CORS configuration, connection-level rate limiting
**Confidence:** HIGH

## Summary

Phase 2 addresses five requirements across two distinct concerns. TOK-01/02/03 migrate WebSocket authentication from a URL query parameter (`?token=...`) to a first-frame binary protocol. This is a coordinated breaking change: both `backend/src/routes/terminal.rs` and `android/.../TerminalSocket.kt` must be updated simultaneously. The existing binary protocol already defines frame types 0x00–0x04; a new `Auth` frame at 0x05 is the natural extension.

NET-01 and NET-02 are independent of each other and independent of the token migration. NET-01 replaces `CorsLayer::permissive()` in `backend/src/lib.rs` with an explicit origin allowlist — a one-file change. NET-02 applies the existing `RateLimiter` to the WebSocket upgrade path, which currently bypasses the rate limiter entirely (the `ws_handler` in `terminal.rs` reads the token from the query string without calling `state.rate_limiter.check()`).

All three backend changes (protocol, CORS, WS rate limiting) are in Rust with Axum 0.7. The Android change is in Kotlin using OkHttp 4.12. The project uses `subtle` for constant-time comparison already — no new dependencies are needed for any of these changes.

**Primary recommendation:** Implement TOK-01/02/03 as a single atomic plan (one commit per language boundary: backend protocol + Android protocol), then NET-01 and NET-02 as a second plan. This satisfies the constraint that the breaking WS protocol change must ship simultaneously on both sides.

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| TOK-01 | WebSocket auth uses first binary frame instead of URL query param | New Auth frame type 0x05 added to protocol.rs; WsQuery struct and Query extractor removed from ws_handler |
| TOK-02 | Backend validates auth frame before accepting terminal data | ws_handler reads first frame, pattern-matches Frame::Auth, rejects and closes on any other first frame |
| TOK-03 | Android sends auth token as first frame after connection | TerminalSocket.connect() passes token; onOpen callback sends Frame.Auth before ping loop starts |
| NET-01 | CorsLayer uses explicit origin allowlist instead of permissive() | CorsLayer::new().allow_origin([...]) with origins from config; requires new allowed_origins field in ServerConfig |
| NET-02 | WebSocket endpoint applies rate limiting before token validation | ws_handler (or a middleware layer) calls state.rate_limiter.check(ip) before reading any frame |
</phase_requirements>

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| axum | 0.7 | WebSocket server, routing | Already in use |
| axum::extract::ws | 0.7 (bundled) | WebSocket message types (`Message::Binary`, `WebSocket::recv`) | Only WS API available in Axum |
| tower-http CorsLayer | 0.5 | CORS enforcement | Already in use, already imported with `cors` feature |
| subtle | 2 | Constant-time comparison for token | Already in use |
| okhttp3 | 4.12.0 | Android WebSocket client | Already in use |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| axum::extract::ConnectInfo | 0.7 | Extract peer IP in ws_handler | Required for rate limiting in ws_handler (same pattern as auth.rs) |
| tokio::time::timeout | 1 | Timeout on first-frame read | Prevent stalled unauthenticated connections |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| First-frame auth (chosen) | WS upgrade header (`Sec-WebSocket-Protocol`) | Headers are inconsistently supported by Android HTTP clients — decision is locked |
| First-frame auth (chosen) | HTTP header before upgrade | Not possible after upgrade completes |
| tower-http CorsLayer | Custom middleware | CorsLayer already imported; no reason to hand-roll |

**Installation:** No new dependencies needed. All required crates are already in `Cargo.toml`.

## Architecture Patterns

### Recommended Project Structure

No new files are strictly required. Changes touch:
```
backend/src/
├── protocol.rs          # Add Frame::Auth(Vec<u8>) variant + 0x05 type constant
├── routes/terminal.rs   # Remove WsQuery/Query, add rate-limit check + first-frame auth handshake
├── lib.rs               # Replace CorsLayer::permissive() with explicit origins
└── config.rs            # Add allowed_origins: Vec<String> to ServerConfig

android/app/src/main/kotlin/com/remotecontrol/data/websocket/
├── Protocol.kt          # Add Frame.Auth sealed class variant + TYPE_AUTH = 0x05
└── TerminalSocket.kt    # Remove ?token= from URL; send Frame.Auth in onOpen before ping
```

### Pattern 1: First-Frame Auth Handshake (Backend)

**What:** After WebSocket upgrade, read the first frame with a timeout. Only `Frame::Auth` is accepted. Any other frame type or timeout closes the connection immediately.

**When to use:** All WebSocket endpoints that require authentication.

**Example:**
```rust
// Source: axum WebSocket docs + project's existing protocol.rs pattern
async fn handle_socket(mut socket: WebSocket, state: Arc<AppState>, session_id: String) {
    let first = match tokio::time::timeout(
        std::time::Duration::from_secs(10),
        socket.recv(),
    ).await {
        Ok(Some(Ok(Message::Binary(data)))) => data,
        _ => {
            let _ = socket.close().await;
            return;
        }
    };

    let token = match Frame::decode(&first) {
        Ok(Frame::Auth(t)) => t,
        _ => {
            let frame = Frame::SessionEvent(SessionEventPayload {
                event_type: "unauthorized".to_string(),
                session_id: session_id.clone(),
            });
            let _ = socket.send(Message::Binary(frame.encode().into())).await;
            let _ = socket.close().await;
            return;
        }
    };

    let expected = state.config.auth.token.as_bytes();
    if expected.len() != token.len() || expected.ct_eq(&token).unwrap_u8() != 1 {
        let _ = socket.close().await;
        return;
    }
    // ... rest of existing handle_socket logic
}
```

### Pattern 2: Rate Limiting in ws_handler (Before Upgrade)

**What:** Extract the peer IP using `ConnectInfo<SocketAddr>` and call `state.rate_limiter.check(ip)` before calling `ws.on_upgrade(...)`. This matches the pattern already used in `auth.rs`.

**When to use:** Any endpoint that must enforce per-IP rate limits.

**Example:**
```rust
pub async fn ws_handler(
    ws: WebSocketUpgrade,
    State(state): State<Arc<AppState>>,
    Path(session_id): Path<String>,
    ConnectInfo(addr): ConnectInfo<SocketAddr>,
) -> Result<impl IntoResponse, AppError> {
    if !state.rate_limiter.check(addr.ip()) {
        return Err(AppError::RateLimited);
    }
    if !TmuxManager::session_exists(&session_id).await {
        return Err(AppError::NotFound(format!("Session {} not found", session_id)));
    }
    Ok(ws.on_upgrade(move |socket| handle_socket(socket, state, session_id)))
}
```

### Pattern 3: Explicit CORS Origins

**What:** Replace `CorsLayer::permissive()` with `CorsLayer::new()` that explicitly lists allowed origins. Origins come from config.toml so they are configurable without recompile.

**When to use:** Any production-facing HTTP service.

**Example:**
```rust
// Source: tower-http CorsLayer docs
use tower_http::cors::{CorsLayer, AllowOrigin};
use axum::http::HeaderValue;

let origins: Vec<HeaderValue> = state.config.server.allowed_origins
    .iter()
    .filter_map(|o| o.parse().ok())
    .collect();

let cors = CorsLayer::new()
    .allow_origin(AllowOrigin::list(origins))
    .allow_methods([axum::http::Method::GET, axum::http::Method::POST,
                    axum::http::Method::PUT, axum::http::Method::DELETE])
    .allow_headers([axum::http::header::CONTENT_TYPE,
                    axum::http::header::AUTHORIZATION]);
```

### Pattern 4: Auth Frame (Android — send as first frame)

**What:** After `onOpen`, send `Frame.Auth` with the raw token bytes before starting the ping loop. The token is no longer included in the URL.

**Example (Kotlin):**
```kotlin
override fun onOpen(webSocket: WebSocket, response: Response) {
    webSocket.send(Frame.Auth(token.toByteArray()).encode().toByteString())
    _state.value = ConnectionState.CONNECTED
    reconnector.reset()
    consecutivePingFailures = 0
    startPing()
}
```

### Anti-Patterns to Avoid

- **Sending the rejection inside `handle_socket` via SessionEvent only:** Always close the socket after rejection, don't just send an error frame and leave the connection open.
- **Moving CORS config check outside of router layer:** `CorsLayer` must be the outermost layer (added last, applied first) to intercept preflight requests before they reach route handlers.
- **Storing the token in the URL for reconnect:** After the migration, `currentUrl` in `TerminalSocket` must NOT contain `?token=`. The token must be passed separately and used only in the `onOpen` callback.
- **Applying rate limiting inside `handle_socket`:** Rate limiting must happen at the HTTP upgrade stage (before the WebSocket is established) to count connection attempts, not message counts.
- **Setting `allowed_origins` to an empty list at startup:** If `allowed_origins` is empty or not configured, the server should either reject all cross-origin requests or log a prominent warning. Do not silently fall back to permissive.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Constant-time token compare | Custom byte-by-byte loop | `subtle::ConstantTimeEq` | Timing side-channel vulnerability in naive impl; already in use |
| CORS enforcement | Custom `tower::Layer` | `tower_http::cors::CorsLayer` | Handles preflight, varies-header, wildcard edge cases; already imported |
| Rate limiting state | New data structure | Existing `RateLimiter` in `rate_limit.rs` | Already handles window reset, consecutive failures, block duration |

**Key insight:** This phase reuses all existing infrastructure. The only net-new code is the `Auth` frame type (6 lines in protocol.rs) and the first-frame handshake logic in `handle_socket` (~20 lines).

## Common Pitfalls

### Pitfall 1: Forgetting to Pass State into handle_socket

**What goes wrong:** `ws.on_upgrade(move |socket| handle_socket(socket, session_id))` — the current `handle_socket` signature does not take `AppState`. After adding first-frame token validation, state must be passed in.

**Why it happens:** The original code validated the token in the HTTP handler (before upgrade), so `handle_socket` never needed state. After moving auth into the socket handler, state is required.

**How to avoid:** Change `handle_socket` signature to `async fn handle_socket(mut socket: WebSocket, state: Arc<AppState>, session_id: String)`. Pass `state.clone()` in the `on_upgrade` closure.

**Warning signs:** Compilation error: "cannot move `state` out of closure" or "use of moved value".

### Pitfall 2: Token Leaks in Reconnect URL

**What goes wrong:** `TerminalSocket.currentUrl` stores the full URL including `?token=...`. After migration, if `currentUrl` is used for reconnect, it would reconnect with the old URL — either failing (token rejected) or leaking the token in logs if logging is added later.

**Why it happens:** The current `connect()` method builds the URL including the token, then stores it in `currentUrl` for reconnect.

**How to avoid:** Store `baseUrl`, `sessionId`, and `token` as separate fields. Reconstruct the URL (without token) for reconnect. Pass token to `doConnect` separately for use in `onOpen`.

**Warning signs:** Reconnection attempts fail with a rejection frame after the migration.

### Pitfall 3: CorsLayer Position in Middleware Stack

**What goes wrong:** Placing `CorsLayer` after `TraceLayer` causes trace logs to fire for rejected CORS preflight requests, and in some configurations causes CORS headers to be missing on responses.

**Why it happens:** Axum layers execute in reverse registration order. `CorsLayer` must intercept before most other middleware.

**How to avoid:** Register `CorsLayer` as the last `.layer()` call (it wraps everything beneath it). In the current `lib.rs`, it already comes before `TraceLayer` — keep this order.

**Warning signs:** OPTIONS preflight returns 404 instead of 200 with CORS headers.

### Pitfall 4: AUTH Frame Rejection Not Closing the Connection

**What goes wrong:** Server sends a rejection `SessionEvent` frame but does not call `socket.close()`. The Android client receives the rejection, emits it as an incoming frame, but the connection remains open — consuming resources.

**Why it happens:** The `handle_socket` function is structured as a loop. Returning early from a match arm doesn't close the socket unless explicitly called.

**How to avoid:** Always call `let _ = socket.close().await;` before returning from `handle_socket` in the auth failure path.

**Warning signs:** Success criteria 2 fails — client receives rejection but connection is not terminated.

### Pitfall 5: Rate Limiter Misses WebSocket Connections

**What goes wrong:** The current `ws_handler` does not call `state.rate_limiter.check()`. Adding rate limiting after the `WebSocketUpgrade` extraction is correct; adding it inside `handle_socket` (after upgrade) is incorrect — at that point the TCP connection and HTTP upgrade are already complete.

**Why it happens:** The rate limit check must be at the HTTP layer, before `ws.on_upgrade()` is called. Success criteria 5 requires counting connection attempts, not frames.

**How to avoid:** Add `ConnectInfo(addr): ConnectInfo<SocketAddr>` as a parameter to `ws_handler` and call `state.rate_limiter.check(addr.ip())` before the `TmuxManager::session_exists` check.

**Warning signs:** Success criteria 5 fails — 100 connection attempts are not rate-limited.

### Pitfall 6: allowed_origins Field Missing from Config

**What goes wrong:** `ServerConfig` in `config.rs` does not have an `allowed_origins` field. Adding CORS config requires extending the struct. Existing `config.toml` files that don't have this field will fail to parse.

**Why it happens:** `toml::from_str` with `#[derive(Deserialize)]` will error on missing required fields.

**How to avoid:** Use `#[serde(default)]` on the `allowed_origins` field in `ServerConfig` so it defaults to an empty `Vec<String>` when absent. Add a reasonable default (e.g., `["http://localhost"]`) or emit a log warning when the list is empty.

**Warning signs:** Compilation succeeds but server panics at startup on existing config.toml.

## Code Examples

Verified patterns from Axum and OkHttp:

### Auth Frame Protocol Wire Format (0x05)
```
[0x05][token bytes (UTF-8)]
```
No length prefix needed — the entire frame payload after the type byte is the token. Matches the existing pattern for `Data` frames (0x00).

### Rust: Frame::Auth Variant Addition
```rust
// Source: existing protocol.rs pattern
const TYPE_AUTH: u8 = 0x05;

// Add to Frame enum:
Auth(Vec<u8>),

// Add to encode():
Frame::Auth(token) => {
    let mut buf = Vec::with_capacity(1 + token.len());
    buf.push(TYPE_AUTH);
    buf.extend_from_slice(token);
    buf
}

// Add to decode():
TYPE_AUTH => Ok(Frame::Auth(data[1..].to_vec())),
```

### Kotlin: Frame.Auth Variant Addition
```kotlin
// Source: existing Protocol.kt pattern
data class Auth(val token: ByteArray) : Frame() {
    override fun equals(other: Any?) = other is Auth && token.contentEquals(other.token)
    override fun hashCode() = token.contentHashCode()
}

// In companion object:
private const val TYPE_AUTH: Byte = 0x05

// In encode():
is Auth -> byteArrayOf(TYPE_AUTH) + token

// In decode():
TYPE_AUTH -> Auth(data.copyOfRange(1, data.size))
```

### Rust: Timeout on First Frame
```rust
// Source: tokio::time::timeout docs
use tokio::time::{timeout, Duration};

let first_msg = timeout(Duration::from_secs(10), socket.recv()).await;
```

### config.toml: New Field
```toml
[server]
host = "127.0.0.1"
port = 48322
allowed_origins = ["http://192.168.1.100:3000", "http://localhost:3000"]
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Token in URL query string | First-frame binary auth | Phase 2 (this) | Token no longer appears in WS upgrade HTTP log lines |
| CorsLayer::permissive() | Explicit origin allowlist | Phase 2 (this) | Cross-origin requests from unknown origins rejected at HTTP layer |
| No rate limiting on WS upgrade | Rate limiting before upgrade | Phase 2 (this) | Connection flood protection at TCP/HTTP level |

**No deprecated patterns to note** — all changes are additive modifications to existing code paths.

## Open Questions

1. **What default value for `allowed_origins` in config.toml?**
   - What we know: The field must exist in `ServerConfig`. Existing config.toml has no such field.
   - What's unclear: Whether to default to `["http://localhost"]`, empty list with warning, or require explicit configuration.
   - Recommendation: Default to empty list with a `tracing::warn!` at startup if empty. Let the planner decide whether to add a sample value to config.toml.

2. **Should the `record_failure` path be called on auth frame rejection?**
   - What we know: `auth.rs` calls `state.rate_limiter.record_failure(ip)` on token mismatch. The WS handler will need to do the same for first-frame auth failures.
   - What's unclear: How to get the `ip` back inside `handle_socket` (it's available in `ws_handler`'s scope, so it must be passed in).
   - Recommendation: Pass `ip: IpAddr` as a parameter to `handle_socket`.

3. **Reconnect token storage in TerminalSocket**
   - What we know: `currentUrl` currently stores the full URL with `?token=`. After migration, token must not be in the URL.
   - What's unclear: Whether to store token as a field on `TerminalSocket` or pass it through `doConnect`.
   - Recommendation: Store `baseUrl: String?`, `currentSessionId: String?`, and `currentToken: String?` as separate fields. Reconstruct URL in `doConnect` without token; pass token separately for use in `onOpen`.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework (Rust) | cargo test (built-in) |
| Framework (Android) | JUnit 4 + kotlinx-coroutines-test |
| Config file | No separate file — `Cargo.toml` dev-dependencies |
| Quick run command (Rust) | `cd /home/gluz/code/remote-control/backend && cargo test 2>&1` |
| Quick run command (Android) | `cd /home/gluz/code/remote-control/android && ./gradlew :app:testDebugUnitTest 2>&1` |
| Full suite command | Both of the above |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| TOK-01 | Frame::Auth encodes/decodes correctly at 0x05 | unit | `cargo test -p remote-control-backend protocol 2>&1` | Wave 0 |
| TOK-01 | Android Frame.Auth encodes/decodes correctly | unit | `./gradlew :app:testDebugUnitTest --tests "*.ProtocolTest" 2>&1` | Wave 0 |
| TOK-02 | ws_handler rejects non-Auth first frame and closes | unit | `cargo test -p remote-control-backend ws_auth 2>&1` | Wave 0 |
| TOK-02 | ws_handler accepts Auth frame and continues session | unit | `cargo test -p remote-control-backend ws_auth 2>&1` | Wave 0 |
| TOK-03 | TerminalSocket sends Auth frame before ping | unit | `./gradlew :app:testDebugUnitTest --tests "*.TerminalSocketTest" 2>&1` | Wave 0 |
| NET-01 | Unknown Origin receives CORS rejection | unit | `cargo test -p remote-control-backend cors 2>&1` | Wave 0 |
| NET-01 | Allowed Origin receives CORS success headers | unit | `cargo test -p remote-control-backend cors 2>&1` | Wave 0 |
| NET-02 | 100+ WS connection attempts from one IP within window are rate-limited | unit | `cargo test -p remote-control-backend rate_limit 2>&1` | Partial — `rate_limit.rs` tests exist for REST paths, not WS |

### Sampling Rate
- **Per task commit:** `cd /home/gluz/code/remote-control/backend && cargo test 2>&1`
- **Per wave merge:** Both backend `cargo test` and Android `./gradlew :app:testDebugUnitTest`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `backend/src/protocol.rs` — existing tests cover 0x00–0x04 only; add `auth_frame_roundtrip` test for 0x05
- [ ] `backend/tests/ws_auth_test.rs` — covers TOK-02: reject non-Auth first frame, accept Auth frame, timeout path
- [ ] `backend/tests/cors_test.rs` — covers NET-01: known origin passes, unknown origin rejected
- [ ] `android/app/src/test/.../ProtocolTest.kt` — extend existing file with `authFrameRoundtrip` test
- [ ] `android/app/src/test/.../TerminalSocketTest.kt` — covers TOK-03: Auth frame sent before ping in onOpen

## Sources

### Primary (HIGH confidence)
- Axum 0.7 source (`axum::extract::ws` module) — WebSocket upgrade, `WebSocket::recv`, `Message::Binary`
- tower-http 0.5 `CorsLayer` docs — `CorsLayer::new()`, `AllowOrigin::list()`
- Project source files read directly — `protocol.rs`, `terminal.rs`, `lib.rs`, `rate_limit.rs`, `auth.rs`, `config.rs`
- OkHttp 4.12 `WebSocketListener` — `onOpen` callback guarantee

### Secondary (MEDIUM confidence)
- `subtle` crate docs (version 2) — `ConstantTimeEq` usage confirmed from existing `auth.rs` and `terminal.rs`
- Existing test infrastructure — `tests/common/mod.rs`, `tests/auth_test.rs` patterns

### Tertiary (LOW confidence)
- None — all findings are grounded in source code read directly

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all libraries already in use, no new dependencies
- Architecture: HIGH — patterns derived directly from existing codebase structure
- Pitfalls: HIGH — identified from direct code analysis (not generic patterns)

**Research date:** 2026-03-31
**Valid until:** 2026-04-30 (stable stack, no fast-moving dependencies)
