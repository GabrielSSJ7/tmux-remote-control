# Technology Stack

**Analysis Date:** 2026-03-31

## Languages

**Primary:**
- Rust 2021 edition - Backend WebSocket server and terminal management
- Kotlin 1.9.22 - Android application UI and API integration
- XML - Android manifest and configuration

**Secondary:**
- SQL - SQLite database schema and queries
- TOML - Configuration files

## Runtime

**Backend:**
- Tokio async runtime (1.x) - Asynchronous task execution for I/O operations

**Android:**
- Android Runtime (ART) - API level 26+ (Android 8.0+)
- JVM Target 17 - Kotlin compilation target

**Package Manager:**
- Cargo - Rust dependency management (Cargo.toml lock files committed)
- Gradle 8.2.2 - Android dependency management with Kotlin DSL

## Frameworks

**Backend Core:**
- Axum 0.7 - Async web framework with WebSocket support (`axum::extract::ws`)
- Tower 0.4 - Middleware and service composition
- Tower-HTTP 0.5 - HTTP middleware (CORS, tracing)

**Android UI:**
- Jetpack Compose 2024.02.00 - Modern declarative UI framework
- Compose Navigation 2.7.7 - Screen navigation
- Activity Compose 1.8.2 - Activity integration with Compose

**Android Lifecycle:**
- Lifecycle ViewModel Compose 2.7.0 - ViewModel state management
- Lifecycle Runtime Compose 2.7.0 - Lifecycle-aware Compose integration

**Testing:**
- JUnit 4.13.2 - Unit testing (Kotlin/Java)
- Mockk 1.13.9 - Kotlin mocking library
- Kotlinx Coroutines Test 1.8.0 - Async testing utilities

## Key Dependencies

**Critical - Backend:**
- `tokio` 1.x - Async runtime and thread pool management
- `sqlx` 0.7 - SQLite database access with compile-time query checking
- `portable-pty` 0.8 - PTY (pseudoterminal) management for shell sessions
- `axum` 0.7 - HTTP/WebSocket server framework

**Critical - Android:**
- `retrofit2` 2.9.0 - HTTP REST client with type-safe API definitions
- `okhttp3` 4.12.0 - HTTP client with interceptor chain (auth, logging)
- `gson` 2.10.1 - JSON serialization/deserialization
- `androidx.compose:compose-bom` 2024.02.00 - Compose dependency management

**Infrastructure:**
- `tracing` 0.1 - Structured logging framework
- `tracing-subscriber` 0.3 - Tracing subscriber with JSON and env-filter support
- `serde` 1.x - Serialization framework for JSON/TOML
- `serde_json` 1.x - JSON serialization
- `toml` 0.8 - TOML parsing for config files
- `uuid` 1.x - UUID v4 generation for session/command IDs
- `chrono` 0.4 - Timestamp handling with serde support
- `subtle` 2.x - Constant-time comparison (authentication)
- `hex` 0.4 - Hex encoding for tokens

**Android Data Persistence:**
- `androidx.datastore:datastore-preferences` 1.0.0 - Key-value preferences store (server URL, token, settings)

## Configuration

**Backend:**
- TOML configuration file: `config.toml`
- Structure:
  - `[server]`: host, port (default 0.0.0.0:48322)
  - `[auth]`: token (64-character hex, auto-generated if empty)
  - `[terminal]`: scrollback_lines, default_shell

**Android:**
- Datastore-based preferences for:
  - `server_url` - Server base URL (string)
  - `token` - Bearer token (string)
  - `font_size` - Terminal font size in SP (int, default 8)
  - `dark_mode` - Theme preference (boolean, default true)
  - `scrollback_lines` - Terminal scrollback buffer (int, default 10000)

**Logging:**
- Backend: JSON structured logging via `tracing-subscriber` with `EnvFilter` (default "info" level)
- Environment variable: `RUST_LOG` controls log level filtering

**Network Security:**
- Android cleartext traffic permitted for development (configured in `network_security_config.xml`)
- HTTP/HTTPS on both, WebSocket upgrade via `ws://` or `wss://` URL scheme conversion

## Build Configuration

**Backend:**
- Rust edition: 2021
- Target: x86_64-unknown-linux-gnu (primary development platform)
- Release optimizations enabled
- SQLx features: `runtime-tokio`, `sqlite`
- Axum features: `ws` (WebSocket support)
- Tower-HTTP features: `cors`, `trace`
- Tracing-subscriber features: `env-filter`, `json`
- UUID features: `v4`

**Android:**
- Gradle version: 8.2.2
- Kotlin version: 1.9.22
- Compose compiler extension: 1.5.10
- Compile SDK: 35 (Android 15)
- Min SDK: 26 (Android 8.0)
- Target SDK: 35 (Android 15)
- Java/Kotlin target: 17
- Release: ProGuard minification enabled

## Platform Requirements

**Development:**
- Linux (tested on Arch Linux)
- Rust toolchain (rustup)
- tmux (for session management)
- For Android: Android SDK 26+, gradle build tools

**Production:**
- Linux server with tmux installed
- Port 48322 (configurable) exposed
- Android 8.0+ device (API 26)

---

*Stack analysis: 2026-03-31*
