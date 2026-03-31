# Testing Patterns

**Analysis Date:** 2026-03-31

## Test Framework

**Backend (Rust):**
- Runner: `tokio` test runtime
- Test attribute: `#[tokio::test]` for async tests, `#[test]` for sync tests
- Config: None explicit (uses Cargo test defaults)

**Android (Kotlin):**
- Runner: JUnit 4 via `AndroidJUnitRunner`
- Test attribute: `@Test`
- Config: `build.gradle.kts` specifies `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"`

**Assertion Library:**

Rust:
- Built-in macros: `assert_eq!()`, `assert!()`, `assert_ne!()`
- Status assertions: `response.status()` from axum testing

Kotlin:
- JUnit: `assertEquals()`, `assertTrue()`, `assertFalse()`, `assertNotNull()`, `assertArrayEquals()`
- mockk: `coEvery()`, `coVerify()`, `any()` for mocking

**Run Commands:**

Rust:
```bash
cargo test                    # Run all tests
cargo test -- --nocapture    # Show output
cargo test --lib             # Unit tests only
```

Kotlin:
```bash
./gradlew test                # Run unit tests
./gradlew connectedAndroidTest  # Run instrumentation tests
./gradlew test --info         # Verbose output
```

## Test File Organization

**Location (Rust):**
- Path: `backend/tests/` (integration tests, outside `src/`)
- Path: Inside `src/` files with `#[cfg(test)] mod tests { ... }`

Both patterns observed:
- Integration tests: `backend/tests/auth_test.rs`, `backend/tests/commands_test.rs`, `backend/tests/health_test.rs`
- Unit tests: `backend/src/error.rs` (lines 41-66), `backend/src/config.rs` (lines 48-101)

**Location (Kotlin):**
- Path: `android/app/src/test/kotlin/...` (mirrors main structure)
- Naming: `{ModuleName}Test.kt`

Examples:
- `android/app/src/test/kotlin/com/remotecontrol/ui/sessions/SessionsViewModelTest.kt`
- `android/app/src/test/kotlin/com/remotecontrol/data/websocket/ProtocolTest.kt`
- `android/app/src/test/kotlin/com/remotecontrol/terminal/TerminalEmulatorTest.kt`

**Naming:**
- File: `*Test.kt` or `*_test.rs`
- Class: `{SubjectName}Test`
- Function: `methodName{expectation}` or `methodNameWhen{condition}Then{result}`

Examples from codebase:
- `loadSessionsFetchesFromApi()` - action + expectation
- `sgrSetsForegroundColor()` - action + result
- `resizePreservesContent()` - action + expectation
- `createSessionCallsApiAndReloads()` - action + side effect

## Test Structure

**Rust Pattern:**

Module imports + test state factory:
```rust
// backend/tests/auth_test.rs
mod common;

use axum::body::Body;
use axum::http::{Request, StatusCode};
use tower::ServiceExt;

async fn make_app() -> Router {
    let state = common::test_state().await;
    Router::new()...
}

#[tokio::test]
async fn test_name() {
    let app = make_app().await;
    let response = app
        .oneshot(Request::builder()...)
        .await
        .unwrap();
    assert_eq!(response.status(), StatusCode::OK);
}
```

Test setup via helper function (`backend/tests/common/mod.rs`):
- Creates in-memory SQLite database
- Runs migrations from `migrations/001_initial.sql`
- Sets up test AppState with predefined token (`TEST_TOKEN`)
- Returns `Arc<AppState>` for dependency injection

**Kotlin Pattern:**

Test setup with @Before/@After lifecycle:
```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class SessionsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val api = mockk<SessionsApi>()

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun loadSessionsFetchesFromApi() = runTest {
        coEvery { api.list() } returns sessions
        val vm = SessionsViewModel(api)
        vm.loadSessions(); advanceUntilIdle()
        assertEquals(sessions, vm.sessions.value)
    }
}
```

Setup components:
- Test dispatcher: `StandardTestDispatcher` for controlled coroutine execution
- Lifecycle hooks: `@Before` (setup), `@After` (teardown)
- Run block: `runTest { ... }` for async test block
- Time advancement: `advanceUntilIdle()` to force coroutine completion

## Mocking

**Rust Framework:** Manual mocking via test doubles

Pattern (from `backend/tests/auth_test.rs`):
```rust
// Create in-memory app with test state
let app = make_app().await;

// Make request with test token in header
let response = app
    .oneshot(
        Request::builder()
            .header("authorization", format!("Bearer {}", common::TEST_TOKEN))
            .body(Body::empty())
            .unwrap(),
    )
    .await
    .unwrap();
```

Test data setup: Configure test state with known values
- `TEST_TOKEN = "test-token-abc123"`
- In-memory DB with known schema
- No mocking library; uses real components with test configuration

**Kotlin Framework:** `mockk` library

Pattern (from `android/app/src/test/kotlin/com/remotecontrol/ui/sessions/SessionsViewModelTest.kt`):
```kotlin
private val api = mockk<SessionsApi>()

@Test
fun loadSessionsFetchesFromApi() = runTest {
    coEvery { api.list() } returns listOf(
        Session(id = "1", name = "main", createdAt = "2024-01-01", attached = false)
    )
    val vm = SessionsViewModel(api)
    vm.loadSessions(); advanceUntilIdle()
    assertEquals(sessions, vm.sessions.value)
    coVerify { api.list() }
}
```

Mocking capabilities:
- `mockk<T>()` - Create mock of interface T
- `coEvery { ... } returns ...` - Setup coroutine mock return value
- `coEvery { ... } throws ...` - Setup mock to throw exception
- `any()` - Match any argument
- `coVerify { ... }` - Assert mock was called
- `atLeast = N` - Verify call count

**What to Mock:**
- External dependencies (API clients, WebSocket)
- Domain services (SessionsApi, CommandsApi)

**What NOT to Mock:**
- Protocol parsing/encoding (ProtocolTest uses real Frame class)
- Utility classes (Reconnector is not mocked)
- State containers (Terminal state is real)

## Fixtures and Factories

**Rust Test Data (backend/tests/common/mod.rs):**

Factory function approach:
```rust
pub async fn test_state() -> Arc<AppState> {
    let db = init_test_pool().await;
    let config = Config { ... };
    Arc::new(AppState { db, config, rate_limiter: ... })
}
```

Constants:
```rust
pub const TEST_TOKEN: &str = "test-token-abc123";
```

Database initialization:
```rust
async fn init_test_pool() -> sqlx::SqlitePool {
    let pool = SqlitePoolOptions::new()
        .max_connections(1)
        .connect("sqlite::memory:")
        .await
        .unwrap();
    let sql = include_str!("../../migrations/001_initial.sql");
    for statement in sql.split(';')... {
        sqlx::query(statement).execute(&pool).await.unwrap();
    }
    pool
}
```

Location: `backend/tests/common/mod.rs`

**Kotlin Test Data (inline in tests):**

Inline factory in tests:
```kotlin
val sessions = listOf(
    Session(id = "1", name = "main", createdAt = "2024-01-01", attached = false),
    Session(id = "2", name = "dev", createdAt = "2024-01-02", attached = true),
)
```

Constructor arguments in assertions:
```kotlin
Command("1", "push", "git push", null, "git", null, "", "")
```

No dedicated fixture files; all test data inline in test methods.

## Coverage

**Requirements:** Not explicitly enforced (no CI checks observed)

**Observed Coverage:**

Backend (Rust):
- `error.rs` - 3/3 variants tested (100%)
- `config.rs` - 3/3 functions tested (100%)
- `auth.rs` - All paths tested (100%)
- Route handlers - Happy path + error cases covered

Android (Kotlin):
- `SessionsViewModelTest.kt` - 4 test cases (load, error, create, delete)
- `CommandsViewModelTest.kt` - 3 test cases (grouping, search, create)
- `ProtocolTest.kt` - 7 test cases (roundtrip, errors)
- `TerminalEmulatorTest.kt` - 7 test cases (text, ANSI, resize)
- `ReconnectorTest.kt` - 4 test cases (delay progression, reset)

**View Coverage:**
```bash
# Rust
cargo tarpaulin  # Generate coverage report (not currently integrated)

# Kotlin
./gradlew jacocoTestReport  # Not configured; no task found
```

## Test Types

**Unit Tests (Rust):**

Scope: Individual functions and error handling
Location: Inline in source files (`backend/src/error.rs`, `backend/src/config.rs`)

Example (`backend/src/error.rs`):
```rust
#[test]
fn not_found_returns_404() {
    let err = AppError::NotFound("session not found".into());
    let response = err.into_response();
    assert_eq!(response.status(), StatusCode::NOT_FOUND);
}
```

Approach: Direct function calls, assert response properties

**Integration Tests (Rust):**

Scope: Full HTTP handlers + database
Location: `backend/tests/` directory

Example (`backend/tests/commands_test.rs`):
```rust
#[tokio::test]
async fn create_and_list_commands() {
    let app = make_app().await;
    let response = app
        .oneshot(Request::builder()
            .method("POST")
            .uri("/commands")
            .header("content-type", "application/json")
            .body(Body::from(r#"{"name":"Deploy",...}"#))
            .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(response.status(), StatusCode::CREATED);
    let body = response.into_body().collect().await.unwrap().to_bytes();
    let created: serde_json::Value = serde_json::from_slice(&body).unwrap();
    assert_eq!(created["name"], "Deploy");
}
```

Approach: Make HTTP requests, parse JSON responses, verify state

**Unit Tests (Kotlin):**

Scope: ViewModel logic, protocol parsing, utilities
Location: `android/app/src/test/kotlin/`

Examples:
- `SessionsViewModelTest.kt` - State flow updates, error handling
- `ProtocolTest.kt` - Frame encoding/decoding roundtrips
- `TerminalEmulatorTest.kt` - ANSI parsing, cursor movement
- `ReconnectorTest.kt` - Exponential backoff logic

Approach: Create instance, call method, assert state changes

**E2E Tests:** Not implemented

## Common Patterns

**Async Testing (Kotlin):**

Pattern:
```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
@Test
fun testName() = runTest {
    // Setup
    coEvery { api.method() } returns value

    // Execute
    viewModel.action()
    advanceUntilIdle()  // Wait for all coroutines

    // Assert
    assertEquals(expected, viewModel.state.value)
    coVerify { api.method() }
}
```

Key points:
- `runTest { ... }` creates test coroutine scope
- `advanceUntilIdle()` forces completion of pending coroutines
- `Dispatchers.setMain(testDispatcher)` in @Before for predictability
- `coEvery` / `coVerify` for suspend functions

**Error Testing (Kotlin):**

Pattern:
```kotlin
@Test
fun loadSessionsSetsErrorOnFailure() = runTest {
    coEvery { api.list() } throws RuntimeException("Network error")
    val vm = SessionsViewModel(api)
    vm.loadSessions(); advanceUntilIdle()
    assertNotNull(vm.error.value)
    assertTrue(vm.error.value!!.contains("Network error"))
}
```

Approach: Mock method throws exception, verify error state is populated

**Error Testing (Rust):**

Pattern in `backend/tests/commands_test.rs`:
```rust
#[tokio::test]
async fn delete_nonexistent_returns_404() {
    let app = make_app().await;
    let response = app
        .oneshot(Request::builder()
            .method("DELETE")
            .uri("/commands/nonexistent")
            .header("authorization", format!("Bearer {}", common::TEST_TOKEN))
            .body(Body::empty())
            .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(response.status(), StatusCode::NOT_FOUND);
}
```

Approach: Send invalid request, verify error status code

**Protocol Testing (Kotlin):**

Roundtrip testing pattern (`android/app/src/test/kotlin/com/remotecontrol/data/websocket/ProtocolTest.kt`):
```kotlin
@Test
fun dataFrameRoundtrip() {
    val frame = Frame.Data("hello terminal".toByteArray())
    val encoded = frame.encode()
    assertEquals(0x00.toByte(), encoded[0])
    val decoded = Frame.decode(encoded)
    assertTrue(decoded is Frame.Data)
    assertArrayEquals("hello terminal".toByteArray(), (decoded as Frame.Data).payload)
}
```

Approach: Create object → encode → decode → verify equality

**Invalid Input Testing (Kotlin):**

Pattern:
```kotlin
@Test(expected = IllegalArgumentException::class)
fun emptyFrameThrows() {
    Frame.decode(byteArrayOf())
}

@Test(expected = IllegalArgumentException::class)
fun unknownTypeThrows() {
    Frame.decode(byteArrayOf(0xFF.toByte()))
}
```

Approach: Use `@Test(expected = Exception::class)` to assert exception type

---

*Testing analysis: 2026-03-31*
