# External Integrations

**Analysis Date:** 2026-03-31

## APIs & External Services

**Terminal Multiplexer Integration:**
- tmux - Session and window management
  - Client: `portable-pty` 0.8 crate
  - Location: `backend/src/tmux.rs`
  - Integration points:
    - `backend/src/routes/sessions.rs` - List, create, delete sessions
    - `backend/src/routes/terminal.rs` - Attach PTY to tmux session
  - Commands: `tmux list-sessions`, `tmux new-session`, `tmux kill-session`, `tmux attach-session`

## Data Storage

**Databases:**
- SQLite (default)
  - Connection: `sqlite:data.db?mode=rwc` (file-based, auto-create)
  - Client library: `sqlx` 0.7 with `runtime-tokio` and `sqlite` features
  - Pool: Max 5 concurrent connections
  - Migrations: Embedded SQL in `backend/migrations/001_initial.sql` (loaded via `include_str!`)
  - Location: `backend/src/db.rs`

**File Storage:**
- Local filesystem only
  - Configuration: `config.toml` (TOML format)
  - Database: `data.db` (SQLite binary file)
  - No external cloud storage

**Caching:**
- None - No external cache
- Rate limiter uses in-memory IP maps (no persistence)

## Authentication & Identity

**Auth Provider:**
- Custom token-based (no OAuth, no third-party provider)
  - Location: `backend/src/auth.rs` - `AuthToken` request guard
  - Transport: HTTP `Authorization: Bearer {token}` header
  - Token format: 64-character lowercase hex (32 bytes random)
  - Generation: `backend/src/config.rs` - Auto-generates via `crypto::rand` if empty
  - Validation: Constant-time comparison using `subtle::ConstantTimeEq`
  - Storage: `config.toml` `[auth]` section

**Rate Limiting:**
- IP-based rate limiting
  - Location: `backend/src/rate_limit.rs`
  - Limits:
    - 5 requests per 60 seconds (per IP)
    - 3 requests per hour (per IP, hourly window)
  - Tracked via in-memory map, no persistence
  - Resets on successful auth

**Client-Side Token Management (Android):**
- Storage: `androidx.datastore:datastore-preferences` 1.0.0
  - Location: `android/app/src/main/kotlin/com/remotecontrol/data/settings/SettingsStore.kt`
  - Key: `"token"` (string preference)
  - Accessed by: `android/app/src/main/kotlin/com/remotecontrol/data/api/ApiClient.kt`
  - Included in all HTTP requests via OkHttp interceptor

## Networking

**REST API Endpoints:**

```
GET    /health                       Health check
GET    /sessions                     List all tmux sessions
POST   /sessions                     Create new session (with optional name)
DELETE /sessions/{id}                Kill tmux session
GET    /sessions/{id}/status         Get session status
POST   /sessions/{id}/exec           Execute stored command in session
GET    /sessions/{id}/terminal       WebSocket upgrade to terminal
GET    /commands                     List stored commands
POST   /commands                     Create new stored command
PUT    /commands/{id}                Update stored command
DELETE /commands/{id}                Delete stored command
```

Location: `backend/src/routes/mod.rs` (route definitions)

**WebSocket Endpoints:**
- URL: `/sessions/{session_id}/terminal?token={token}`
- Authentication: Token as query parameter (not header)
- Location: `backend/src/routes/terminal.rs` (ws_handler, handle_socket)
- Protocol: Binary frame format
  - Frame types defined in `backend/src/protocol.rs`
  - Frame struct: Data(Vec<u8>), Resize, Ping, Pong, SessionEvent
- Features:
  - Terminal I/O relay to tmux session via PTY
  - Resize frames to change terminal dimensions
  - Ping/Pong heartbeat every 15 seconds
  - Session events (ended, detached, error) sent back to client
  - Automatic disconnect on 3 consecutive ping failures

**HTTP Client Configuration (Android):**
- Location: `android/app/src/main/kotlin/com/remotecontrol/data/api/ApiClient.kt`
- Framework: Retrofit 2.9.0 + OkHttp 4.12.0
- Interceptors:
  - Authorization (adds Bearer token header)
  - Optional: Logging interceptor (4.12.0)
- Timeouts: 10 seconds (connect + read)
- JSON serialization: Gson with `LOWER_CASE_WITH_UNDERSCORES` field naming

**WebSocket Client Configuration (Android):**
- Location: `android/app/src/main/kotlin/com/remotecontrol/data/websocket/TerminalSocket.kt`
- Framework: OkHttp WebSocket
- Auto-reconnection: Exponential backoff via `android/app/src/main/kotlin/com/remotecontrol/util/Reconnector.kt`
- Ping interval: 15 seconds
- Failure threshold: 3 consecutive ping failures

## Network Security

**HTTP Configuration:**
- Default host: 0.0.0.0 (all interfaces)
- Default port: 48322 (configurable)
- CORS: Enabled via `tower-http` (all origins allowed)
- Cleartext: Permitted (no TLS enforcement in config)

**Android Network Security:**
- File: `android/app/src/main/res/xml/network_security_config.xml`
- Policy: Cleartext traffic permitted (development config)
- Trust: System certificates only

## Monitoring & Observability

**Structured Logging:**
- Framework: `tracing` 0.1 + `tracing-subscriber` 0.3
- Format: JSON structured output
- Filter: `EnvFilter` (env variable `RUST_LOG`, default "info")
- Location: `backend/src/main.rs` (initialization)
- Scope: Backend only (no Android client logging framework)

**Metrics & Tracing:**
- None - No Prometheus, Datadog, or similar
- Local observability only

**Error Tracking:**
- None - No Sentry or similar integration
- Errors returned in HTTP responses via `AppError` enum
- Location: `backend/src/error.rs`

## Environment Configuration

**Backend Configuration File:**
- Path: `config.toml` (loaded at startup)
- Format: TOML
- Sections:
  ```
  [server]
  host = "0.0.0.0"
  port = 48322

  [auth]
  token = "..."  # Auto-generated if empty

  [terminal]
  scrollback_lines = 10000
  default_shell = "/bin/bash"
  ```
- Location handling: `backend/src/config.rs`

**Backend Environment Variables:**
- `RUST_LOG` - Logging level filter (optional, defaults to "info")

**Android Configuration:**
- Stored in `androidx.datastore:datastore-preferences`
- Keys stored in `SettingsStore.kt`:
  - `server_url` (string) - Base URL
  - `token` (string) - Bearer token
  - `font_size` (int) - Terminal font size in SP
  - `dark_mode` (boolean) - Theme selection
  - `scrollback_lines` (int) - Terminal history buffer
- No environment variables used

**API Client Configuration (Android):**
- Base URL: Configured at runtime from `server_url` preference
- Token: Retrieved from `token` preference on each request

## Secrets Management

**Backend Secrets:**
- Location: `config.toml` `[auth]` section
- Token: 64-character hex string
- Generated automatically on first startup if empty
- Stored in plaintext in config file (development setup)

**Android Secrets:**
- Storage: `androidx.datastore:datastore-preferences`
- Server URL + token stored by user at app launch
- Stored in plaintext (development setup)
- No encryption at rest

**Security Notes:**
- Constant-time comparison for token validation (mitigates timing attacks)
- Rate limiting on auth failures (5 per 60s per IP)
- No TLS enforcement in current setup

## Terminal Integration

**PTY (Pseudo-Terminal) Management:**
- Library: `portable-pty` 0.8
- Location: `backend/src/routes/terminal.rs` (handle_socket)
- Flow:
  1. WebSocket client connects via `/sessions/{session_id}/terminal`
  2. Backend opens PTY via `native_pty_system()`
  3. Spawns `tmux attach-session` process in PTY
  4. Relays bidirectional I/O between WebSocket and PTY
  5. Handles resize frames to set PTY dimensions

**Default Terminal Settings:**
- Rows: 24
- Columns: 80
- Default shell: Configured in `config.toml` (default `/bin/bash`)
- Scrollback: Configured in settings (default 10000 lines)

**Binary Frame Protocol:**
- Definition: `backend/src/protocol.rs`
- Frame types:
  - `0x00` Data: Terminal I/O bytes
  - `0x01` Resize: Columns (u16 BE) + Rows (u16 BE)
  - `0x02` Ping: Keep-alive from client
  - `0x03` Pong: Keep-alive response
  - `0x04` SessionEvent: JSON event payload
- Encoding: Binary serialization via custom encode/decode methods

## Webhooks & Callbacks

**Incoming:**
- None

**Outgoing:**
- Session events sent via WebSocket frames (not external webhooks)
- Event types: session ended, detached, error notifications

---

*Integration audit: 2026-03-31*
