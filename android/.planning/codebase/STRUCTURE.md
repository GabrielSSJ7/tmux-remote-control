# Codebase Structure

**Analysis Date:** 2026-03-31

## Directory Layout

```
remote-control/                          # Monorepo root
├── android/                             # Android client (Kotlin, Jetpack Compose)
│   ├── app/
│   │   ├── src/
│   │   │   ├── main/
│   │   │   │   ├── kotlin/com/remotecontrol/
│   │   │   │   │   ├── data/            # Data layer
│   │   │   │   │   │   ├── api/         # Retrofit REST clients
│   │   │   │   │   │   ├── model/       # Data classes (Session, Command)
│   │   │   │   │   │   ├── settings/    # DataStore preferences
│   │   │   │   │   │   └── websocket/   # OkHttp WebSocket + binary protocol
│   │   │   │   │   ├── navigation/      # Compose NavGraph
│   │   │   │   │   ├── terminal/        # ANSI emulator + Canvas renderer
│   │   │   │   │   ├── ui/             # Feature screens and ViewModels
│   │   │   │   │   │   ├── commands/    # Command library bottom sheet
│   │   │   │   │   │   ├── sessions/    # Sessions list (home screen)
│   │   │   │   │   │   ├── settings/    # Settings screen
│   │   │   │   │   │   ├── terminal/    # Terminal emulator screen
│   │   │   │   │   │   └── theme/       # Material 3 theme
│   │   │   │   │   ├── util/            # Utilities (Reconnector)
│   │   │   │   │   ├── App.kt           # Application singleton
│   │   │   │   │   └── MainActivity.kt  # Single Activity entry point
│   │   │   │   ├── res/
│   │   │   │   │   ├── values/          # themes.xml
│   │   │   │   │   └── xml/             # network_security_config.xml
│   │   │   │   └── AndroidManifest.xml
│   │   │   └── test/
│   │   │       └── kotlin/com/remotecontrol/
│   │   │           ├── data/websocket/  # Protocol tests
│   │   │           ├── terminal/        # Emulator tests
│   │   │           ├── ui/commands/     # CommandsViewModel tests
│   │   │           ├── ui/sessions/     # SessionsViewModel tests
│   │   │           └── util/            # Reconnector tests
│   │   ├── build.gradle.kts             # App dependencies and SDK config
│   │   └── proguard-rules.pro           # R8/ProGuard rules for release
│   ├── build.gradle.kts                 # Project-level plugins (AGP 8.2.2, Kotlin 1.9.22)
│   ├── gradle/wrapper/                  # Gradle wrapper
│   └── settings.gradle.kts
├── backend/                             # Rust backend (Axum)
│   ├── src/
│   │   ├── routes/
│   │   │   ├── mod.rs                   # Router assembly (api_routes())
│   │   │   ├── sessions.rs              # Session CRUD + exec handlers
│   │   │   ├── commands.rs              # Command CRUD handlers
│   │   │   └── terminal.rs              # WebSocket handler with PTY bridge
│   │   ├── auth.rs                      # Bearer token extractor
│   │   ├── config.rs                    # TOML config loader
│   │   ├── db.rs                        # SQLite pool + migration runner
│   │   ├── error.rs                     # AppError enum
│   │   ├── lib.rs                       # create_router(), module exports
│   │   ├── main.rs                      # Binary entry point
│   │   ├── models.rs                    # Request/response structs
│   │   ├── protocol.rs                  # Binary frame codec (matches Android Protocol.kt)
│   │   ├── rate_limit.rs                # IP-based rate limiter
│   │   ├── state.rs                     # AppState (db, config, rate_limiter)
│   │   └── tmux.rs                      # tmux CLI wrapper
│   ├── migrations/
│   │   └── 001_initial.sql              # Schema: commands, config tables
│   ├── tests/
│   │   ├── common/mod.rs                # Test helpers (in-memory DB, test state)
│   │   ├── auth_test.rs                 # Auth integration tests
│   │   ├── commands_test.rs             # Commands CRUD integration tests
│   │   └── health_test.rs               # Health endpoint test
│   ├── Cargo.toml                       # Rust dependencies
│   ├── Cargo.lock
│   ├── config.toml                      # Runtime config (server, auth, terminal)
│   └── data.db                          # SQLite database file (runtime)
├── docs/
│   └── superpowers/
│       ├── plans/                       # Implementation plans
│       └── specs/                       # Design specifications
├── SETUP.md                             # Production setup guide
└── .planning/                           # Analysis documents
    └── codebase/                        # Generated codebase analysis
```

## Directory Purposes

**`android/app/src/main/kotlin/com/remotecontrol/data/api/`:**
- Purpose: Retrofit REST API client definitions
- Contains: `ApiClient.kt` (Retrofit + OkHttp setup, auth interceptor), `SessionsApi.kt` (session endpoints interface), `CommandsApi.kt` (command endpoints interface)
- Key pattern: `ApiClient` builds Retrofit instance with Gson (LOWER_CASE_WITH_UNDERSCORES naming) and auth interceptor. Exposes `.sessions` and `.commands` properties.

**`android/app/src/main/kotlin/com/remotecontrol/data/model/`:**
- Purpose: Kotlin data classes matching backend JSON responses
- Contains: `Session.kt` (Session data class), `Command.kt` (Command, CreateCommand, UpdateCommand data classes)

**`android/app/src/main/kotlin/com/remotecontrol/data/settings/`:**
- Purpose: Local persistent key-value storage
- Contains: `SettingsStore.kt` -- wraps `DataStore<Preferences>` with typed Flow getters and suspend setters
- Keys: `server_url`, `token`, `font_size` (default 8), `dark_mode` (default true), `scrollback_lines` (default 10000)

**`android/app/src/main/kotlin/com/remotecontrol/data/websocket/`:**
- Purpose: WebSocket client and binary protocol codec
- Contains: `TerminalSocket.kt` (connection lifecycle, reconnect, ping), `Protocol.kt` (Frame sealed class with encode/decode)
- Key pattern: `TerminalSocket` exposes `state: StateFlow<ConnectionState>` and `incoming: SharedFlow<Frame>`

**`android/app/src/main/kotlin/com/remotecontrol/terminal/`:**
- Purpose: ANSI terminal emulation engine (no network dependency)
- Contains: `TerminalEmulator.kt` (VT100 state machine, 439 lines), `TerminalRenderer.kt` (Compose Canvas rendering, 153 lines)
- Key pattern: `TerminalEmulator` is a pure data processor (bytes in, cell grid out). `TerminalRenderer` is a Compose Canvas that reads emulator state and draws. Separated by design for testability.

**`android/app/src/main/kotlin/com/remotecontrol/ui/sessions/`:**
- Purpose: Home screen showing tmux sessions
- Contains: `SessionsScreen.kt` (Compose UI with LazyColumn, FAB, error Snackbar), `SessionsViewModel.kt` (load/create/delete sessions via API)

**`android/app/src/main/kotlin/com/remotecontrol/ui/terminal/`:**
- Purpose: Full-screen interactive terminal
- Contains: `TerminalScreen.kt` (layout with terminal renderer, keyboard handling, FAB for commands), `TerminalViewModel.kt` (WebSocket lifecycle, input routing, emulator bridge), `ExtraKeysBar.kt` (Esc, Tab, Ctrl, arrows bar)

**`android/app/src/main/kotlin/com/remotecontrol/ui/commands/`:**
- Purpose: Saved command library (modal bottom sheet over terminal)
- Contains: `CommandsSheet.kt` (ModalBottomSheet with search, grouped list, add dialog), `CommandsViewModel.kt` (CRUD, search/filter, grouped by category)

**`android/app/src/main/kotlin/com/remotecontrol/ui/settings/`:**
- Purpose: App configuration
- Contains: `SettingsScreen.kt` (server URL, token, font size slider, dark mode toggle, scrollback slider), `SettingsViewModel.kt` (reads/writes SettingsStore, updates ApiClient)

**`android/app/src/main/kotlin/com/remotecontrol/ui/theme/`:**
- Purpose: Material 3 theme definition
- Contains: `Theme.kt` (dark/light color schemes, `RemoteControlTheme` composable)

**`android/app/src/main/kotlin/com/remotecontrol/navigation/`:**
- Purpose: Single-Activity navigation graph
- Contains: `NavGraph.kt` (routes: `sessions`, `terminal/{sessionId}`, `settings`)

**`android/app/src/main/kotlin/com/remotecontrol/util/`:**
- Purpose: Shared utility classes
- Contains: `Reconnector.kt` (exponential backoff: 1s base, 30s max, tracks attempt count)

**`backend/src/routes/`:**
- Purpose: All HTTP/WebSocket handler functions
- Contains: `mod.rs` (assembles router with all routes), `sessions.rs` (5 handlers), `commands.rs` (4 handlers), `terminal.rs` (WebSocket upgrade + PTY bridge loop)

**`backend/src/`:**
- Purpose: Flat module structure for backend domain, infrastructure, and glue
- Key files by role:
  - Entry: `main.rs`, `lib.rs`
  - Domain: `models.rs`, `tmux.rs`, `protocol.rs`
  - Infrastructure: `auth.rs`, `rate_limit.rs`, `db.rs`, `config.rs`, `error.rs`, `state.rs`

**`backend/migrations/`:**
- Purpose: SQL migration files loaded at startup
- Contains: `001_initial.sql` (creates `commands` and `config` tables)

**`backend/tests/`:**
- Purpose: Integration tests using in-memory SQLite
- Contains: `common/mod.rs` (shared test state factory), `auth_test.rs`, `commands_test.rs`, `health_test.rs`

**`docs/superpowers/specs/`:**
- Purpose: Design specification documents
- Contains: `2026-03-29-remote-terminal-control-design.md` (full system design spec including API, protocol, screens, security)

## Key File Locations

**Entry Points:**
- `backend/src/main.rs`: Backend binary entry -- loads config, inits DB, starts Axum server
- `backend/src/lib.rs`: Router assembly (`create_router()`), also used by integration tests
- `android/app/src/main/kotlin/com/remotecontrol/App.kt`: Android Application singleton
- `android/app/src/main/kotlin/com/remotecontrol/MainActivity.kt`: Single Activity
- `android/app/src/main/AndroidManifest.xml`: App manifest (permissions, network security config)

**Configuration:**
- `backend/config.toml`: Runtime config (host, port, auth token, terminal settings)
- `backend/Cargo.toml`: Rust dependencies
- `android/app/build.gradle.kts`: Android dependencies (Compose BOM 2024.02.00, Retrofit 2.9.0, OkHttp 4.12.0)
- `android/build.gradle.kts`: AGP 8.2.2, Kotlin 1.9.22
- `android/app/src/main/res/xml/network_security_config.xml`: Cleartext traffic policy

**Protocol (must stay in sync):**
- `backend/src/protocol.rs`: Rust Frame enum with encode/decode
- `android/app/src/main/kotlin/com/remotecontrol/data/websocket/Protocol.kt`: Kotlin Frame sealed class with encode/decode

**Core Logic:**
- `android/app/src/main/kotlin/com/remotecontrol/terminal/TerminalEmulator.kt`: ANSI terminal state machine (439 lines, most complex file)
- `android/app/src/main/kotlin/com/remotecontrol/terminal/TerminalRenderer.kt`: Canvas rendering with gestures
- `android/app/src/main/kotlin/com/remotecontrol/data/websocket/TerminalSocket.kt`: WebSocket lifecycle management
- `backend/src/routes/terminal.rs`: PTY bridge (183 lines, WebSocket-to-tmux loop)
- `backend/src/tmux.rs`: tmux CLI wrapper with input validation

**Database:**
- `backend/migrations/001_initial.sql`: Schema definition
- `backend/src/db.rs`: Pool init + migration runner
- `backend/data.db`: Runtime SQLite database file (gitignored)

**Testing:**
- `android/app/src/test/kotlin/com/remotecontrol/terminal/TerminalEmulatorTest.kt`: Emulator unit tests
- `android/app/src/test/kotlin/com/remotecontrol/data/websocket/ProtocolTest.kt`: Frame codec tests
- `android/app/src/test/kotlin/com/remotecontrol/util/ReconnectorTest.kt`: Backoff logic tests
- `android/app/src/test/kotlin/com/remotecontrol/ui/sessions/SessionsViewModelTest.kt`: ViewModel tests
- `android/app/src/test/kotlin/com/remotecontrol/ui/commands/CommandsViewModelTest.kt`: ViewModel tests
- `backend/tests/auth_test.rs`: Auth integration tests (valid, missing, wrong token)
- `backend/tests/commands_test.rs`: CRUD integration tests
- `backend/tests/health_test.rs`: Health endpoint test

## Naming Conventions

**Files (Android):**
- Composable screens: `{Feature}Screen.kt` -- `SessionsScreen.kt`, `TerminalScreen.kt`, `SettingsScreen.kt`
- ViewModels: `{Feature}ViewModel.kt` -- `SessionsViewModel.kt`, `TerminalViewModel.kt`
- Bottom sheets: `{Feature}Sheet.kt` -- `CommandsSheet.kt`
- UI components: `{Descriptive}Bar.kt` -- `ExtraKeysBar.kt`
- API interfaces: `{Resource}Api.kt` -- `SessionsApi.kt`, `CommandsApi.kt`
- Models: `{Entity}.kt` -- `Session.kt`, `Command.kt`
- Packages: singular nouns -- `data`, `api`, `model`, `ui`, `terminal`, `util`

**Files (Backend):**
- Route modules: `{resource}.rs` -- `sessions.rs`, `commands.rs`, `terminal.rs`
- Infrastructure: `{concern}.rs` -- `auth.rs`, `config.rs`, `db.rs`, `error.rs`, `rate_limit.rs`, `state.rs`
- Domain: `{concept}.rs` -- `models.rs`, `protocol.rs`, `tmux.rs`
- Tests: `{feature}_test.rs` -- `auth_test.rs`, `commands_test.rs`, `health_test.rs`
- Migrations: `{number}_{name}.sql` -- `001_initial.sql`

**Directories:**
- Android: feature-based under `ui/` (sessions, terminal, settings, commands), layer-based at top level (data, terminal, navigation, util)
- Backend: flat `src/` modules, `routes/` subdirectory for handlers, `tests/` for integration tests

**Classes/Functions:**
- Kotlin: PascalCase for classes/composables, camelCase for functions and properties
- Rust: snake_case for functions and modules, PascalCase for types and enums

## Where to Add New Code

**New Android Screen:**
1. Create `ui/{feature}/{Feature}Screen.kt` (composable)
2. Create `ui/{feature}/{Feature}ViewModel.kt` (if needs state/logic)
3. Add route in `navigation/NavGraph.kt`
4. If API calls needed: add methods to existing `data/api/{Resource}Api.kt` or create new interface
5. Tests: `test/kotlin/com/remotecontrol/ui/{feature}/{Feature}ViewModelTest.kt`

**New Backend Route:**
1. Add handler function in `backend/src/routes/{resource}.rs` (existing or new file)
2. If new file: add `pub mod {resource};` in `backend/src/routes/mod.rs`
3. Register route in `api_routes()` in `backend/src/routes/mod.rs`
4. Add request/response structs in `backend/src/models.rs`
5. Integration test: `backend/tests/{resource}_test.rs`

**New Data Model:**
- Android: `android/.../data/model/{Entity}.kt`
- Backend: add struct in `backend/src/models.rs`

**New WebSocket Frame Type:**
- Backend: add variant to `Frame` enum in `backend/src/protocol.rs`, update `encode()` and `decode()`
- Android: add subclass to `Frame` sealed class in `android/.../data/websocket/Protocol.kt`, update `encode()` and companion `decode()`
- Handle in `backend/src/routes/terminal.rs` select loop and `android/.../ui/terminal/TerminalViewModel.kt` collect block

**New Database Table:**
1. Create `backend/migrations/002_{name}.sql`
2. The migration runner in `backend/src/db.rs` splits on `;` and runs all statements from all migration files -- but currently only loads `001_initial.sql` via `include_str!`. A new migration requires updating the runner.
3. Add model struct with `#[derive(sqlx::FromRow)]` in `backend/src/models.rs`

**New Utility (Android):**
- Place in `android/.../util/{UtilName}.kt`
- Tests in `android/app/src/test/kotlin/com/remotecontrol/util/{UtilName}Test.kt`

**New Terminal Feature (ANSI sequence):**
- Add handling in `TerminalEmulator.kt` in the appropriate state handler (`processCsi`, `processEscape`, `processSgr`, `processDecPrivateMode`)
- Test in `android/app/src/test/kotlin/com/remotecontrol/terminal/TerminalEmulatorTest.kt`

## Special Directories

**`android/build/` and `android/app/build/`:**
- Purpose: Gradle build output (compiled classes, APK, generated sources)
- Generated: Yes
- Committed: No

**`android/.gradle/`:**
- Purpose: Gradle daemon cache
- Generated: Yes
- Committed: No

**`backend/target/`:**
- Purpose: Cargo build output (compiled binaries, dependencies)
- Generated: Yes
- Committed: No

**`android/gradle/wrapper/`:**
- Purpose: Gradle wrapper JAR and properties
- Generated: No
- Committed: Yes

**`android/app/src/main/res/`:**
- Purpose: Android resources (themes, XML configs)
- Generated: No
- Committed: Yes

**`backend/migrations/`:**
- Purpose: SQL migration files, loaded at runtime via `include_str!`
- Generated: No
- Committed: Yes

**`docs/superpowers/`:**
- Purpose: Design specs and implementation plans
- Generated: No
- Committed: Yes

**`.planning/codebase/`:**
- Purpose: Generated analysis documents for AI-assisted development
- Generated: Yes (by map-codebase)
- Committed: Yes

---

*Structure analysis: 2026-03-31*
