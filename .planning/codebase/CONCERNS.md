# Codebase Concerns

**Analysis Date:** 2026-03-31

## Security Concerns

**Single token-based authentication:**
- Issue: The entire system uses a single plaintext token stored in `config.toml`. Compromise of this file or token exposure is catastrophic.
- Files: `backend/src/auth.rs`, `backend/src/config.rs`, `backend/config.toml`
- Current mitigation: Constant-time comparison via `subtle` crate to prevent timing attacks
- Recommendations:
  - Implement multi-factor authentication or client certificate pinning for production
  - Use environment variables or secrets manager instead of hardcoded config files
  - Add audit logging for all authentication attempts
  - Consider rotating token requirements after first use
  - Document security implications in setup guide

**Token in URL query parameters:**
- Issue: WebSocket token passed in URL query string (`/sessions/:id/terminal?token=...`) can be logged in server logs, browser history, and proxy logs
- Files: `backend/src/routes/terminal.rs`, `android/app/src/main/kotlin/com/remotecontrol/data/websocket/TerminalSocket.kt`
- Risk: Token leakage through infrastructure logging
- Recommendation: Use Authorization header in WebSocket upgrade handshake instead of query parameters

**Permissive CORS configuration:**
- Issue: `CorsLayer::permissive()` allows any origin to access all endpoints
- Files: `backend/src/lib.rs` line 25
- Impact: Any website can make cross-origin requests to the backend if exposed to internet
- Fix approach: Configure explicit allow-list of trusted origins based on Android app package name/origin

**No rate limiting on WebSocket upgrade:**
- Issue: WebSocket endpoint checks rate limit but the ws_handler creates new PTY per connection without resource limits
- Files: `backend/src/routes/terminal.rs`
- Risk: Resource exhaustion through unlimited concurrent PTY creation
- Recommendation: Implement per-session or per-IP concurrent connection limits

**Insufficient input validation:**
- Issue: Command execution via `send_keys` does minimal sanitization. While session names are validated, command payloads are sent directly to tmux
- Files: `backend/src/tmux.rs` line 90-102
- Risk: Command injection if an app user can manipulate what commands are executed
- Mitigation: Session name validation is in place, but command content is user-controlled (by design)
- Recommendation: Document that commands are executed as-is; don't add validation that might break legitimate shell commands

**No TLS enforcement:**
- Issue: No requirement for HTTPS/WSS in default config
- Files: `backend/config.toml`, `SETUP.md` (section 3 is optional)
- Impact: Credentials and terminal data transmitted in cleartext over network
- Recommendation: Make TLS mandatory in production; provide automatic setup instructions

**Session ID enumeration:**
- Issue: Session IDs are UUIDs (guessable prefix `rc-`), and `/sessions/:id/terminal` endpoint accepts any string
- Files: `backend/src/routes/terminal.rs` line 31, `backend/src/routes/sessions.rs` line 29
- Risk: Attacker can brute-force or guess session IDs if token is compromised
- Recommendation: Use cryptographically strong session IDs (already using UUIDs, but prefix is predictable)

---

## Performance Bottlenecks

**In-memory rate limiter with unbounded growth:**
- Problem: Rate limiter stores state for every IP that connects. No cleanup mechanism for old entries.
- Files: `backend/src/rate_limit.rs`
- Cause: `HashMap<IpAddr, IpState>` grows indefinitely; stale entries are never removed
- Improvement path:
  - Implement LRU eviction or periodic cleanup
  - Add metrics to monitor map size
  - Set reasonable limits on concurrent IPs (e.g., max 10K entries)

**Terminal rendering with manual gesture handling:**
- Problem: TerminalRenderer uses low-level gesture event loops instead of higher-level compose semantics
- Files: `android/app/src/main/kotlin/com/remotecontrol/terminal/TerminalRenderer.kt` (lines 40-80+)
- Impact: Custom pointer event handling is complex and difficult to optimize for Compose recomposition
- Recommendation: Consider using Compose's built-in zoom and scroll modifiers if possible

**Database queries without indexing strategy:**
- Problem: Commands are queried by ID frequently but no index is defined
- Files: `backend/migrations/001_initial.sql`, `backend/src/routes/commands.rs`, `backend/src/routes/sessions.rs`
- Current capacity: SQLite with default settings handles small to medium datasets well
- Improvement path: Add explicit CREATE INDEX statements for frequently queried columns (id, name)

**WebSocket reader on blocking thread:**
- Problem: PTY I/O uses `spawn_blocking` which ties up a tokio runtime thread, limiting concurrent sessions
- Files: `backend/src/routes/terminal.rs` lines 91-115
- Cause: `portable-pty` reader doesn't support async I/O
- Scaling limit: Default tokio runtime may limit to 100s of concurrent sessions
- Improvement path:
  - Use `tokio::io::AsyncRead` wrapper or switch to async PTY library if available
  - Monitor blocking task queue depth
  - Set explicit max blocking threads if high concurrency needed

---

## Fragile Areas

**WebSocket connection state management:**
- Files: `android/app/src/main/kotlin/com/remotecontrol/data/websocket/TerminalSocket.kt`
- Why fragile: Multiple state variables (`_state`, `webSocket`, `reconnectJob`, `pingJob`, `consecutivePingFailures`) can become desynchronized if exceptions occur
- Symptoms: Connection state shows CONNECTED but socket is actually dead; ping jobs leak
- Safe modification: Refactor state into single sealed class; ensure all state transitions go through single point
- Test coverage: No explicit integration tests for disconnection/reconnection scenarios

**Terminal emulator state + scroll offset:**
- Files: `android/app/src/main/kotlin/com/remotecontrol/terminal/TerminalRenderer.kt`
- Why fragile: Multiple mutable state variables (`currentFontSize`, `scrollOffset`, `charHeightPx`) updated during gesture handling without locks
- Symptoms: Scroll position can jump; font size changes can be inconsistent if recomposition occurs during gesture
- Safe modification: Use single immutable state object updated via single handler
- Test coverage: TerminalEmulatorTest exists but does not test gesture state consistency

**Tmux session name validation in a thin layer:**
- Files: `backend/src/tmux.rs` line 9-17
- Why fragile: Validation is alphanumeric + `-_` only. If tmux commands change or new special cases arise, validation might be bypassed
- Current regex: `[a-zA-Z0-9_-]+` max 64 chars
- Safe modification: Maintain explicit whitelist in tests; add docstring explaining why this exact set is safe
- Risk if not tested: Injection attacks via future tmux version changes

**Protocol encoding/decoding mismatch between Rust and Kotlin:**
- Files: `backend/src/protocol.rs`, `android/app/src/main/kotlin/com/remotecontrol/data/websocket/Protocol.kt`
- Why fragile: Two independent implementations of binary protocol. Resize frame uses different byte order handling (Rust uses `to_be_bytes`, Kotlin uses `ByteBuffer` which is big-endian by default, but mask operations differ)
- Symptoms: Resize frames might decode incorrectly on edge cases
- Safe modification: Add property-based tests with same test vectors in both languages; document byte order explicitly
- Test coverage: ProtocolTest exists for Kotlin but focuses on happy path

---

## Scaling Limits

**SQLite as primary database:**
- Current capacity: Single file, good for up to ~1000 commands and unlimited read concurrency
- Limit: Database locking on writes becomes problematic with >10 concurrent writers
- Scaling path: Migrate to PostgreSQL or add read replicas if write concurrency becomes bottleneck

**Single tmux server per backend:**
- Current capacity: Handles 100s of sessions limited by PTY resources
- Limit: One backend instance = one tmux server; no horizontal scaling
- Scaling path: Implement session affinity and multiple backend instances with session registry

**Hardcoded rate limiter settings:**
- Current: 5 requests per 60 seconds, 10 failures = 3600s block
- Problem: Fixed at compile time in `backend/src/main.rs` line 18
- Scaling path: Move to config file; allow dynamic adjustment without recompilation

---

## Dependencies at Risk

**portable-pty 0.8:**
- Risk: Crate is maintained but not in active development; blocking I/O limitation may become problematic at scale
- Impact: Terminal sessions are CPU-limited; no async PTY means scaling stops at ~100 concurrent sessions
- Migration plan: Monitor for async PTY implementations; consider `pty-rs` or fork if needed

**okhttp3 4.12.0 in Android:**
- Risk: Older version; security patches may lag behind latest
- Impact: Potential WebSocket or TLS vulnerabilities
- Recommendation: Keep dependency up-to-date; set up automated dependency scanning

**No explicit dependency versions in Android Gradle:**
- Risk: Transitive dependencies can change behavior unexpectedly
- Impact: Build reproducibility not guaranteed
- Recommendation: Lock transitive dependency versions explicitly

---

## Testing Gaps

**No integration tests for WebSocket session flow:**
- What's not tested: Full lifecycle of creating session → attaching → sending commands → detaching
- Files: All WebSocket handling in `backend/src/routes/terminal.rs` and `android/app/src/main/kotlin/com/remotecontrol/data/websocket/TerminalSocket.kt`
- Risk: Disconnection and reconnection logic is untested; race conditions in cleanup code
- Priority: High (affects core user flow)

**No tests for rate limiter cleanup:**
- What's not tested: Behavior when rate limiter map grows; whether stale entries are removed
- Files: `backend/src/rate_limit.rs`
- Risk: Memory leak under long-term operation with varied IPs
- Priority: Medium (production operational concern)

**No Android instrumentation tests:**
- What's not tested: API interaction, WebSocket communication, UI rendering
- Files: All Android app code
- Risk: Regressions in real app behavior; broken endpoints not caught until manual testing
- Priority: High (user-facing functionality)

**Protocol test coverage incomplete:**
- What's not tested: Malformed frame handling, edge cases in size calculation
- Files: Both `backend/src/protocol.rs` (lines 52-75) and Android Protocol.kt
- Risk: Potential panic or crash on unexpected input
- Priority: Medium (security-adjacent)

**No tests for gesture state consistency:**
- What's not tested: Concurrent gesture events; recomposition during zoom/scroll
- Files: `android/app/src/main/kotlin/com/remotecontrol/terminal/TerminalRenderer.kt`
- Risk: Incorrect scroll position or font size in edge cases
- Priority: Low (UX issue, not functional)

---

## Known Operational Issues

**Token regeneration requires config file write:**
- Issue: Changing the token requires editing `config.toml` and restarting backend
- Files: `backend/src/config.rs`, `SETUP.md` section 6
- Workaround: Manually edit config and restart
- Recommendation: Implement token rotation endpoint (if authentication is restructured)

**No health check endpoint for monitoring:**
- Issue: `/health` exists and returns "ok" but doesn't check database or tmux connectivity
- Files: `backend/src/lib.rs` line 16
- Impact: Load balancer/monitoring can't distinguish between "service up" and "service broken but running"
- Recommendation: Add detailed health checks for db pool, tmux, and rate limiter status

**Unhandled error in frame encoding:**
- Issue: `Frame::encode()` for SessionEvent uses `.unwrap()` on JSON serialization
- Files: `backend/src/protocol.rs` line 43
- Risk: Panic if JSON serialization fails (low risk but not production-clean)
- Fix: Change to `?` operator with Result return type, or use serialization that can't fail

**No graceful shutdown handling:**
- Issue: `axum::serve().await.unwrap()` means server panics if serving fails; no cleanup of active sessions
- Files: `backend/src/main.rs` line 30
- Impact: Active terminal sessions may be orphaned without notification to clients
- Recommendation: Implement signal handling (SIGTERM) with graceful session closure

**Database migrations are manual string parsing:**
- Issue: `run_migrations` splits on `;` which is fragile for complex SQL
- Files: `backend/src/db.rs` line 15
- Risk: Comments or quoted semicolons could break migration
- Recommendation: Use `sqlx::migrate!` compile-time checked migrations

---

## Missing Critical Features

**No session persistence across backend restart:**
- Problem: If backend crashes/restarts, all in-memory session references are lost (though tmux sessions remain running)
- Blocks: Reliable session recovery; graceful shutdown
- Fix approach: Store session metadata in database; implement recovery on startup

**No connection metrics or monitoring:**
- Problem: No visibility into active connections, command execution count, or error rates
- Blocks: Operational observability; debugging production issues
- Fix approach: Add Prometheus metrics; expose `/metrics` endpoint

**No multi-user support:**
- Problem: Single token grants access to all sessions and commands
- Blocks: Using this in a multi-user environment (though it's designed for personal use)
- Design note: Intended as single-user tool, but worth documenting limitation

**No command confirmation or dry-run mode:**
- Problem: Commands are executed immediately without preview
- Blocks: Safe execution of destructive commands (e.g., `rm -rf /`)
- Recommendation: Add optional preview mode or command validation hooks

---

## Security Debt Summary

| Issue | Severity | Effort | Impact |
|-------|----------|--------|--------|
| Single shared token | High | Medium | Complete compromise if exposed |
| Token in URL | High | Low | Leakage through logs |
| Permissive CORS | Medium | Low | Cross-origin attacks if exposed |
| WebSocket rate limiting | Medium | Medium | DoS via connection spam |
| No TLS enforcement | High | Low | Cleartext credentials/output |
| Session ID predictability | Low | Low | Enumeration attacks if token leaked |

---

## Performance Debt Summary

| Issue | Severity | Effort | Impact |
|-------|----------|--------|--------|
| Unbounded rate limiter | Low | Medium | Memory leak over months |
| Terminal gesture handling | Low | High | Scroll/zoom UX inconsistency |
| Blocking PTY I/O | Medium | High | Limits to ~100 concurrent sessions |
| No database indexing | Low | Low | Slow queries if commands grow to 100K+ |

---

*Concerns audit: 2026-03-31*
