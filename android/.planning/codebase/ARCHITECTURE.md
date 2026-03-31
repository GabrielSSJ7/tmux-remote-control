# Architecture

**Analysis Date:** 2026-03-31

## Pattern Overview

**Overall:** Client-Server monorepo with WebSocket real-time terminal bridge. A Rust backend manages tmux sessions via PTY and exposes REST + WebSocket APIs. A Kotlin Android app provides the remote terminal UI using Jetpack Compose MVVM.

**Key Characteristics:**
- Two-tier: Android client talks to a self-hosted Rust backend over HTTP REST (CRUD) and WebSocket (terminal I/O)
- Backend bridges Android client to tmux sessions via `portable-pty` -- spawns `tmux attach-session` inside a PTY
- Custom binary WebSocket protocol: first byte = type tag (0x00-0x04), rest = payload
- No DI framework on Android -- manual wiring through `App` (Application subclass) singleton
- MVVM on Android with Compose UI + ViewModel + StateFlow
- Backend uses Axum extractors for auth, `Arc<AppState>` for shared state

## Layers

**Android: UI Layer:**
- Purpose: Compose screens, user interaction, navigation
- Location: `android/app/src/main/kotlin/com/remotecontrol/ui/`
- Contains: `SessionsScreen.kt`, `TerminalScreen.kt`, `SettingsScreen.kt`, `CommandsSheet.kt`, `ExtraKeysBar.kt`
- Depends on: ViewModels, `App` instance, data models, theme
- Used by: `NavGraph` via Compose Navigation

**Android: ViewModel Layer:**
- Purpose: Business logic, state management, coroutine scoping
- Location: Co-located with screens in `android/app/src/main/kotlin/com/remotecontrol/ui/*/`
- Contains: `SessionsViewModel.kt`, `TerminalViewModel.kt`, `CommandsViewModel.kt`, `SettingsViewModel.kt`
- Depends on: API interfaces, `TerminalSocket`, `TerminalEmulator`, `SettingsStore`
- Used by: Screens via `remember { ViewModel(...) }`

**Android: Data Layer:**
- Purpose: Network communication (REST and WebSocket), local persistence
- Location: `android/app/src/main/kotlin/com/remotecontrol/data/`
- Contains: `api/ApiClient.kt`, `api/SessionsApi.kt`, `api/CommandsApi.kt`, `websocket/TerminalSocket.kt`, `websocket/Protocol.kt`, `settings/SettingsStore.kt`, `model/Session.kt`, `model/Command.kt`
- Depends on: OkHttp, Retrofit, DataStore Preferences, Gson
- Used by: ViewModels

**Android: Terminal Engine:**
- Purpose: ANSI terminal emulation and Compose Canvas rendering
- Location: `android/app/src/main/kotlin/com/remotecontrol/terminal/`
- Contains: `TerminalEmulator.kt` (VT100/ANSI state machine, cell grid, scrollback, alt buffer), `TerminalRenderer.kt` (Canvas drawing, pinch-to-zoom, scroll gestures)
- Depends on: Android Canvas/Paint APIs, Compose UI
- Used by: `TerminalViewModel`, `TerminalScreen`

**Android: Navigation:**
- Purpose: Screen routing
- Location: `android/app/src/main/kotlin/com/remotecontrol/navigation/NavGraph.kt`
- Contains: Single `NavGraph` composable with three routes: `sessions`, `terminal/{sessionId}`, `settings`
- Used by: `MainActivity`

**Android: Utility:**
- Purpose: Cross-cutting helpers
- Location: `android/app/src/main/kotlin/com/remotecontrol/util/Reconnector.kt`
- Contains: Exponential backoff calculator (1s base, 30s max)
- Used by: `TerminalSocket`

**Backend: Routes Layer:**
- Purpose: HTTP and WebSocket request handlers
- Location: `backend/src/routes/`
- Contains: `sessions.rs` (list, create, delete, status, exec), `commands.rs` (list, create, update, delete), `terminal.rs` (WebSocket upgrade + PTY bridge), `mod.rs` (router assembly)
- Depends on: `AppState`, `AuthToken` extractor, `TmuxManager`, `models`, `error`, `protocol`
- Used by: `create_router()` in `backend/src/lib.rs`

**Backend: Domain Layer:**
- Purpose: Business entities, tmux interaction, protocol definition
- Location: `backend/src/` (flat modules)
- Contains: `models.rs` (request/response structs), `tmux.rs` (shell command wrapper), `protocol.rs` (binary frame codec)
- Depends on: `tokio::process::Command`, `serde`, `portable-pty`
- Used by: Routes layer

**Backend: Infrastructure Layer:**
- Purpose: Auth, rate limiting, database, config, error handling, shared state
- Location: `backend/src/`
- Contains: `auth.rs` (Axum `FromRequestParts` extractor), `rate_limit.rs` (IP-based sliding window + failure blocking), `db.rs` (SQLite pool + migration runner), `config.rs` (TOML loader with auto-token generation), `error.rs` (AppError enum), `state.rs` (AppState struct)
- Depends on: `sqlx`, `subtle`, `toml`
- Used by: Routes layer, `main.rs`

## Data Flow

**Terminal Session Interaction (real-time):**

1. Android `TerminalScreen` renders, calls `TerminalViewModel.connect()`
2. `TerminalSocket.connect()` in `android/.../data/websocket/TerminalSocket.kt` builds WebSocket URL: `ws://{host}/sessions/{id}/terminal?token=...`
3. OkHttp opens WebSocket to backend
4. Backend `ws_handler` in `backend/src/routes/terminal.rs` validates token (constant-time compare via `subtle`), checks session exists via `TmuxManager::session_exists()`
5. Backend opens PTY via `portable-pty`, spawns `tmux attach-session -t {sessionId}` inside it
6. Two `spawn_blocking` threads bridge PTY reader/writer to async `mpsc` channels
7. Main loop uses `tokio::select!` to shuttle data between WebSocket and PTY channels
8. PTY output bytes wrapped in `Frame::Data` (byte `0x00` + raw bytes), sent as binary WebSocket message
9. Android `TerminalSocket` receives binary messages, decodes `Frame` via `Protocol.kt`, emits via `SharedFlow`
10. `TerminalViewModel` collects incoming `Frame.Data`, feeds payload to `TerminalEmulator.process()`
11. `TerminalEmulator` parses ANSI escape sequences byte-by-byte, updates 2D cell grid, increments `version`
12. `TerminalRenderer` (Compose Canvas) redraws when `version` StateFlow changes

**User Input (keyboard to backend):**

1. `TerminalScreen` captures key events via `onKeyEvent` modifier on a zero-height focusable Box
2. Printable chars and special keys mapped to byte sequences in `TerminalViewModel.sendKey()` / `sendInput()` / `sendCtrl()`
3. Bytes wrapped in `Frame.Data`, sent via `TerminalSocket.send()` as binary WebSocket message
4. Backend decodes frame in `tokio::select!` loop, writes bytes to PTY writer, which feeds tmux

**REST API Flow (sessions/commands CRUD):**

1. ViewModel calls Retrofit suspend function (e.g., `api.list()`)
2. `ApiClient` auth interceptor in `android/.../data/api/ApiClient.kt` adds `Authorization: Bearer {token}` header
3. Backend `AuthToken` extractor in `backend/src/auth.rs` validates token with rate limiting (checks IP, records failures, resets on success)
4. Route handler executes business logic -- tmux CLI commands for sessions, SQLite queries for commands
5. JSON response returned, deserialized by Gson on Android (field naming: `LOWER_CASE_WITH_UNDERSCORES`)

**State Management (Android):**

- Screens create ViewModels via `remember { ViewModel(dependencies) }` -- no DI framework
- ViewModels expose `StateFlow` properties, screens collect with `collectAsState()`
- `App` singleton holds `SettingsStore` and `ApiClient`, passed through NavGraph to screens
- `SettingsStore` uses DataStore Preferences with `Flow`-based reads and `suspend` writes

## Key Abstractions

**Frame (Binary WebSocket Protocol):**
- Purpose: Type-tagged binary encoding for all WebSocket communication
- Backend: `backend/src/protocol.rs` -- `pub enum Frame { Data(Vec<u8>), Resize { cols, rows }, Ping, Pong, SessionEvent(SessionEventPayload) }`
- Android: `android/.../data/websocket/Protocol.kt` -- `sealed class Frame { Data, Resize, Ping, Pong, SessionEvent }`
- Wire format: byte[0] = type (0x00-0x04), remaining bytes = payload. Both sides must maintain identical encode/decode logic.

**TerminalEmulator:**
- Purpose: Full VT100/ANSI terminal state machine with cell grid, cursor, scrollback, alternate screen
- Location: `android/.../terminal/TerminalEmulator.kt` (439 lines)
- Pattern: Byte-by-byte state machine (`NORMAL`, `ESCAPE`, `CSI`, `OSC`, `UTF8`). Maintains 2D `Array<Array<TerminalCell>>` for main and alt buffers. `version: Long` increments on every `process()` or `resize()` for change detection.
- Supports: SGR attributes (bold, italic, dim, underline, inverse, strikethrough), 256-color + 24-bit RGB, scroll regions, cursor save/restore, DEC private modes (alt screen 1049/1047/47)

**TerminalCell:**
- Purpose: Single character cell with display attributes
- Location: `android/.../terminal/TerminalEmulator.kt` (top of file)
- Fields: `char`, `fg`, `bg`, `bold`, `underline`, `inverse`, `dim`, `italic`, `strikethrough`

**TmuxManager:**
- Purpose: Stateless wrapper around tmux CLI commands
- Location: `backend/src/tmux.rs`
- Pattern: All-static async methods. Validates session names (alphanumeric + hyphens/underscores, max 64 chars). Spawns `tmux` subprocesses via `tokio::process::Command`.
- Methods: `list_sessions()`, `create_session()`, `kill_session()`, `send_keys()`, `session_exists()`

**AppError:**
- Purpose: Unified error type for all backend routes
- Location: `backend/src/error.rs`
- Variants: `NotFound(String)`, `Unauthorized`, `RateLimited`, `BadRequest(String)`, `Internal(String)`, `TmuxError(String)`
- Pattern: Implements Axum `IntoResponse`. Maps to HTTP status codes. JSON body includes `error`, `message`, `request_id` (UUID).

**AuthToken (Axum Extractor):**
- Purpose: Authenticate every REST request
- Location: `backend/src/auth.rs`
- Pattern: Implements `FromRequestParts<Arc<AppState>>`. Extracts Bearer token from `Authorization` header. Uses constant-time comparison via `subtle::ConstantTimeEq`. Integrates rate limiting per IP.

**RateLimiter:**
- Purpose: IP-based request throttling and brute-force protection
- Location: `backend/src/rate_limit.rs`
- Config: 5 attempts per 60s window, block after 10 consecutive failures for 3600s
- Pattern: `Mutex<HashMap<IpAddr, IpState>>` with sliding window + consecutive failure counter

**AppState:**
- Purpose: Shared application state for all route handlers
- Location: `backend/src/state.rs`
- Fields: `db: SqlitePool`, `config: Config`, `rate_limiter: RateLimiter`
- Pattern: Wrapped in `Arc`, passed as Axum router state

**SettingsStore:**
- Purpose: Persistent user preferences on Android
- Location: `android/.../data/settings/SettingsStore.kt`
- Pattern: Wraps DataStore Preferences. Each setting exposed as `Flow<T>` for reactive reads, `suspend` setter functions for writes.
- Keys: `server_url`, `token`, `font_size`, `dark_mode`, `scrollback_lines`

**TerminalSocket:**
- Purpose: WebSocket connection with auto-reconnect and ping keepalive
- Location: `android/.../data/websocket/TerminalSocket.kt`
- Pattern: Wraps OkHttp WebSocket. State machine (`DISCONNECTED` -> `CONNECTING` -> `CONNECTED`). Ping every 15s, 3 missed pongs triggers disconnect. Reconnect via `Reconnector` exponential backoff.

## Entry Points

**Backend Binary:**
- Location: `backend/src/main.rs`
- Triggers: `cargo run` from `backend/` directory
- Responsibilities: Loads `config.toml`, inits SQLite pool with migrations from `backend/migrations/001_initial.sql`, creates rate limiter, builds Axum router, binds TCP listener on configured host:port

**Backend Router Assembly:**
- Location: `backend/src/lib.rs` (`create_router()`)
- Triggers: Called from `main.rs` and directly in integration tests
- Responsibilities: Combines `/health` route, API routes from `routes::api_routes()`, CORS (permissive), trace layer

**Android Application:**
- Location: `android/app/src/main/kotlin/com/remotecontrol/App.kt`
- Triggers: Process startup
- Responsibilities: Creates `SettingsStore`, reads persisted server URL + token, creates `ApiClient`

**Android Activity:**
- Location: `android/app/src/main/kotlin/com/remotecontrol/MainActivity.kt`
- Triggers: App launch
- Responsibilities: Sets Compose content with `RemoteControlTheme`, passes `App` to `NavGraph`

**Android NavGraph:**
- Location: `android/app/src/main/kotlin/com/remotecontrol/navigation/NavGraph.kt`
- Routes: `sessions` (start destination), `terminal/{sessionId}`, `settings`

## API Routes

| Method | Route | Handler | Auth |
|--------|-------|---------|------|
| GET | `/health` | `lib.rs::health()` | No |
| GET | `/sessions` | `routes/sessions.rs::list()` | Bearer |
| POST | `/sessions` | `routes/sessions.rs::create()` | Bearer |
| DELETE | `/sessions/:id` | `routes/sessions.rs::delete()` | Bearer |
| GET | `/sessions/:id/status` | `routes/sessions.rs::status()` | Bearer |
| POST | `/sessions/:id/exec` | `routes/sessions.rs::exec()` | Bearer |
| GET | `/commands` | `routes/commands.rs::list()` | Bearer |
| POST | `/commands` | `routes/commands.rs::create()` | Bearer |
| PUT | `/commands/:id` | `routes/commands.rs::update()` | Bearer |
| DELETE | `/commands/:id` | `routes/commands.rs::delete()` | Bearer |
| GET (WS) | `/sessions/:id/terminal` | `routes/terminal.rs::ws_handler()` | Query param |

## Error Handling

**Backend Strategy:** Enum-based `AppError` with `IntoResponse` implementation

**Backend Patterns:**
- All route handlers return `Result<T, AppError>`
- `AppError` variants map to HTTP status codes (404, 401, 429, 400, 500)
- JSON error body: `{ "error": "code", "message": "...", "request_id": "uuid" }`
- Database errors logged with `tracing::error!` before mapping to `AppError::Internal`
- `TmuxManager` methods return `Result<T, String>`, mapped to `AppError::TmuxError` at route level
- WebSocket errors in `terminal.rs` send `Frame::SessionEvent` with `event_type: "error"` before closing

**Android Strategy:** Try-catch in ViewModel coroutine scopes

**Android Patterns:**
- `SessionsViewModel`: catches exceptions, sets `_error: MutableStateFlow<String?>` for UI display (Snackbar with Retry)
- `CommandsViewModel`: silently swallows all exceptions (empty catch blocks)
- `TerminalSocket.onFailure()`: schedules reconnect via `Reconnector`
- `TerminalSocket.onMessage()`: catches frame decode exceptions silently
- `TerminalEmulator`: coerces cursor positions to valid ranges, returns default `TerminalCell` for out-of-bounds access
- No global error handler or crash reporting

## Cross-Cutting Concerns

**Logging:**
- Backend: `tracing` + `tracing-subscriber` with JSON output and `EnvFilter`. Key events logged at info/error level: WS connect/disconnect, PTY open, tmux spawn, database errors.
- Android: No structured logging framework.

**Validation:**
- Backend: `TmuxManager::validate_session_name()` enforces `[a-zA-Z0-9_-]` max 64 chars. SQL queries use bind parameters. No input validation at route level for command fields.
- Android: Minimal -- checks non-blank for settings before creating API client. No form validation on command creation dialog.

**Authentication:**
- Backend REST: Bearer token in `Authorization` header, validated by `AuthToken` extractor with constant-time comparison
- Backend WebSocket: Token in query parameter `?token=...`, validated with constant-time comparison in `ws_handler`
- Android REST: Token injected via OkHttp auth interceptor in `ApiClient`
- Android WebSocket: Token passed in URL query parameter by `TerminalSocket.connect()`
- Token storage: DataStore Preferences on Android, `config.toml` on backend (auto-generated if empty)

**Reconnection:**
- `TerminalSocket` uses `Reconnector` for exponential backoff (1s, 2s, 4s, ... max 30s)
- Ping every 15s, 3 consecutive pong failures trigger disconnect + reconnect
- `TerminalScreen` shows visual "Disconnected - reconnecting..." banner via `ConnectionState` StateFlow

**Database:**
- Backend only: SQLite via `sqlx` with async runtime
- Single migration: `backend/migrations/001_initial.sql` (tables: `commands`, `config`)
- Manual migration runner in `backend/src/db.rs` (splits SQL on `;`, executes each statement)
- Sessions are NOT stored in DB -- they are live tmux processes queried via CLI

---

*Architecture analysis: 2026-03-31*
