# Coding Conventions

**Analysis Date:** 2026-03-31

## Naming Patterns

**Files (Rust backend):**
- Modules: `snake_case` (`auth.rs`, `rate_limit.rs`, `tmux.rs`)
- Tests: `module_name_test.rs` (`auth_test.rs`, `commands_test.rs`, `health_test.rs`)
- Structs: `PascalCase` (`AppError`, `AuthToken`, `TmuxSession`)
- Constants: `UPPER_SNAKE_CASE` (`TYPE_DATA`, `DEFAULT_FG`, `DEFAULT_BG`)

**Files (Kotlin Android):**
- Classes/Data: `PascalCase` (`Session.kt`, `SessionsViewModel.kt`, `TerminalSocket.kt`)
- Tests: `PascalCase` + `Test` suffix (`SessionsViewModelTest.kt`, `ProtocolTest.kt`, `ReconnectorTest.kt`)
- Packages: `com.remotecontrol.layer.feature` (e.g., `com.remotecontrol.data.api`, `com.remotecontrol.ui.sessions`)

**Functions (Rust):**
- Public: `snake_case` (`list_sessions()`, `create_session()`, `validate_session_name()`)
- Async functions: `async fn` prefix with operation name
- Abbreviations: Spell out fully (`create_session` not `mk_session`)

**Functions (Kotlin):**
- Functions: `camelCase` (`loadSessions()`, `deleteSession()`, `nextDelay()`)
- Private fields: `_fieldName` for backing fields in ViewModels (`_sessions`, `_isLoading`, `_error`)
- Public property: Expose via `StateFlow<T>` without prefix (`sessions`, `isLoading`, `error`)

**Variables:**
- Rust: `snake_case` for bindings and local variables (`current_url`, `reconnect_job`, `max_scrollback`)
- Kotlin: `camelCase` for all variables and properties (`baseUrl`, `sessionId`, `currentFg`)

**Types:**
- Rust: Explicit derives (`#[derive(Debug, Serialize, Deserialize, Clone)]`)
- Rust: Generic bounds inline (`pub fn check(&self, ip: IpAddr) -> bool`)
- Kotlin: Data classes for immutable models (`data class Session(val id: String, ...)`)
- Kotlin: Sealed classes for discriminated unions (`sealed class Frame { ... }`)

## Code Style

**Formatting:**
- Rust: Standard Rust formatting (2-space indentation, enforced by `rustfmt`)
- Kotlin: Standard Kotlin formatting (4-space indentation, no formatter configured)
- Line length: No explicit limits observed; code varies 70-120 chars

**Linting:**
- Rust: No explicit linting config detected; follows Cargo/rustfmt defaults
- Kotlin: No linting tool configured; relies on IDE defaults

**Documentation Comments:**
- Rust: Doc comments (`///`) on public functions and modules
  - Example: `/// Validates a tmux session name, rejecting empty, too-long, or specially-encoded names`
  - Example: `/// Loads config from `path`. If `auth.token` is empty, generates and persists a new token.`
- Kotlin: No doc comments observed; follows minimal documentation approach

## Import Organization

**Rust:**
```rust
use std::...                    // Standard library
use external_crate::...        // External crates
use crate::module::...         // Internal modules
```

Order observed in `backend/src/auth.rs`:
```rust
use axum::extract::FromRequestParts;
use axum::http::request::Parts;
use std::net::{SocketAddr, IpAddr};
use crate::error::AppError;
use crate::state::AppState;
```

**Kotlin:**
```kotlin
import android.* / androidx.*   // Android framework
import com.google.* / com.squareup.*  // Third-party
import com.remotecontrol.*      // Project internal
```

Order observed in `android/app/src/main/kotlin/com/remotecontrol/data/api/ApiClient.kt`:
```kotlin
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
```

**Path Aliases:**
- Not used (no `tsconfig` paths or Cargo aliases observed)

## Error Handling

**Rust Patterns:**

Enum-based errors with explicit variants (`backend/src/error.rs`):
```rust
pub enum AppError {
    NotFound(String),
    Unauthorized,
    RateLimited,
    BadRequest(String),
    Internal(String),
    TmuxError(String),
}
```

Error conversion to HTTP responses via `IntoResponse`:
- Each variant maps to `(StatusCode, message)`
- Structured response: `{error: String, message: String, request_id: String}`
- Request IDs are UUID v4 per request
- NO sensitive data in messages (generic "Database error" not query details)

Usage in routes (`backend/src/routes/sessions.rs`):
```rust
TmuxManager::list_sessions()
    .await
    .map_err(AppError::TmuxError)?;
```

Error logging in handlers:
```rust
.map_err(|e| {
    tracing::error!("Database error: {}", e);
    AppError::Internal("Database error".to_string())
})?
```

**Kotlin Patterns:**

Exception handling via try-catch in coroutines (`android/app/src/main/kotlin/com/remotecontrol/ui/sessions/SessionsViewModel.kt`):
```kotlin
try { _sessions.value = api.list() }
catch (e: Exception) { _error.value = e.message ?: "Failed to load sessions" }
finally { _isLoading.value = false }
```

Validation with `require()` assertions:
```kotlin
require(data.isNotEmpty()) { "Empty frame" }
require(data.size >= 5) { "Resize frame too short" }
```

Exception throwing with `IllegalArgumentException` for contract violations (`android/app/src/main/kotlin/com/remotecontrol/data/websocket/Protocol.kt`):
```kotlin
throw IllegalArgumentException("Unknown frame type: 0x${String.format("%02x", data[0])}")
```

## Logging

**Framework (Rust):** `tracing` crate with structured logging

Initialization in `backend/src/main.rs`:
```rust
tracing_subscriber::fmt()
    .with_env_filter(EnvFilter::from_default_env().add_directive("info".parse().unwrap()))
    .json()
    .init();
```

**Levels:**
- `tracing::info!()` for startup/lifecycle: `"Listening on {}"`
- `tracing::error!()` for failures: `"Database error: {}"`
- Structured JSON output in production

**Kotlin:** No structured logging; limited use of logging observed

## Comments

**When to Comment:**
- Rust: Complex algorithms and security-critical sections get comments
  - Validation logic: `validate_session_name()` has detailed explaining comments
  - Security: `ConstantTimeEq` usage documented inline
- Kotlin: Minimal comments; code is self-documenting through naming

**JSDoc/TSDoc:**
- Rust: Doc comments (`///`) for public functions (mandatory)
  - Example: `/// Generates a cryptographically random 64-character hex token.`
  - Example: `/// Lists all active tmux sessions.`
- Kotlin: No doc comments observed or enforced

**Inline Comments:**
- Rust: Rare; used for non-obvious control flow
- Kotlin: Minimal; tests use inline comments for clarity
  - Example in test: `// Backfill data`

## Function Design

**Size (Rust):**
- Handlers: 15-35 lines (route handlers in `backend/src/routes/`)
- Utility functions: 10-20 lines
- Complex parsing: 30-50 lines (ANSI parser in TerminalEmulator)

**Parameters:**
- Rust: Pass by reference for large types; owned for small types
  - Pattern: `&str` for strings, `String` for owned, `Arc<AppState>` for shared state
  - Destructuring in handlers: `Json(input): Json<CreateSession>`
- Kotlin: Pass by value; immutable data classes
  - Pattern: `name: String?` for optional, `List<T>` for collections

**Return Values:**
- Rust: Result<T, AppError> for fallible operations
  - Example: `async fn list_sessions() -> Result<Vec<TmuxSession>, String>`
  - HTTP handlers: `Result<Json<T>, AppError>` or status code
- Kotlin: Async via `suspend fun` with coroutines
  - ViewModels emit state: `StateFlow<T>` and `SharedFlow<T>`
  - HTTP layer: Returns objects directly, errors propagate via exception

## Module Design

**Exports (Rust):**

Public module exports in `backend/src/lib.rs`:
```rust
pub mod auth;
pub mod config;
pub mod db;
pub mod error;
pub mod models;
pub mod protocol;
pub mod rate_limit;
pub mod routes;
pub mod state;
pub mod tmux;

pub fn create_router() -> Router<Arc<AppState>> { ... }
```

Internal submodule structure (`backend/src/routes/mod.rs`):
```rust
mod commands;
mod sessions;
mod terminal;

pub fn api_routes() -> Router { ... }
```

**Exports (Kotlin):**

Layered package structure:
- `com.remotecontrol.data.*` - API clients, models, WebSocket
- `com.remotecontrol.ui.*` - ViewModels, Screens (Compose)
- `com.remotecontrol.util.*` - Utilities (Reconnector)
- `com.remotecontrol.terminal.*` - Terminal emulator logic

No explicit barrel files; each file defines single class/interface.

**Barrel Files:** Not used in either language

## Async/Concurrency Patterns

**Rust:**
- Async runtime: `tokio` with `#[tokio::test]` for tests
- Error handling: `.map_err()` and `?` operator for Result chaining
- Shared state: `Arc<AppState>` for thread-safe reference counting

**Kotlin:**
- Coroutines: `kotlinx.coroutines` with `viewModelScope.launch` for lifecycle-aware jobs
- Error handling: Try-catch in suspend functions
- State management: `StateFlow<T>` for observable state, `MutableStateFlow` for mutations
- Job management: Store Job handles to cancel operations (e.g., `reconnectJob`, `pingJob`)

## Testing Patterns

See TESTING.md for comprehensive test structure and organization.

---

*Convention analysis: 2026-03-31*
