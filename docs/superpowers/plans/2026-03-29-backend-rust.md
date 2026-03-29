# Backend Rust -- Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Rust backend that exposes terminal sessions (via tmux) over WebSocket and manages a command library via REST API.

**Architecture:** Axum web server with WebSocket + REST routes. Tmux provides session persistence. PTY bridging connects WebSocket clients to tmux sessions in real-time. SQLite stores the command library.

**Tech Stack:** Rust, Axum 0.7, Tokio, SQLx (SQLite), portable-pty, serde, tower-http

---

## File Structure

```
backend/
├── Cargo.toml
├── config.toml
├── migrations/
│   └── 001_initial.sql
├── src/
│   ├── main.rs              -- Entry point, router assembly, server start
│   ├── config.rs             -- Config struct, TOML loading, token generation
│   ├── state.rs              -- AppState shared across handlers
│   ├── error.rs              -- AppError enum, IntoResponse, JSON error bodies
│   ├── models.rs             -- Request/response DTOs
│   ├── auth.rs               -- Bearer token extractor middleware
│   ├── rate_limit.rs         -- IP-based rate limiter
│   ├── db.rs                 -- SQLite pool init, migrations
│   ├── tmux.rs               -- Tmux CLI wrapper (list, create, kill, send-keys)
│   ├── protocol.rs           -- WebSocket binary frame encode/decode
│   └── routes/
│       ├── mod.rs            -- Route tree assembly
│       ├── commands.rs       -- CRUD for command library
│       ├── sessions.rs       -- Session list/create/delete/status/exec
│       └── terminal.rs       -- WebSocket terminal handler (PTY bridge)
└── tests/
    ├── common/
    │   └── mod.rs            -- Test helpers (test app, test state)
    ├── health_test.rs
    ├── auth_test.rs
    ├── commands_test.rs
    └── protocol_test.rs
```

---

### Task 1: Project Scaffold + Health Check

**Files:**
- Create: `backend/Cargo.toml`
- Create: `backend/src/main.rs`

- [ ] **Step 1: Create Cargo.toml**

```toml
[package]
name = "remote-control-backend"
version = "0.1.0"
edition = "2021"

[dependencies]
axum = { version = "0.7", features = ["ws"] }
tokio = { version = "1", features = ["full"] }
sqlx = { version = "0.7", features = ["runtime-tokio", "sqlite"] }
serde = { version = "1", features = ["derive"] }
serde_json = "1"
tower = "0.4"
tower-http = { version = "0.5", features = ["cors", "trace"] }
tracing = "0.1"
tracing-subscriber = { version = "0.3", features = ["env-filter", "json"] }
uuid = { version = "1", features = ["v4"] }
rand = "0.8"
chrono = { version = "0.4", features = ["serde"] }
portable-pty = "0.8"
toml = "0.8"
subtle = "2"

[dev-dependencies]
tower = { version = "0.4", features = ["util"] }
http-body-util = "0.1"
```

- [ ] **Step 2: Create minimal main.rs with health endpoint**

```rust
use axum::{routing::get, Router};
use tokio::net::TcpListener;

async fn health() -> &'static str {
    "ok"
}

fn create_router() -> Router {
    Router::new().route("/health", get(health))
}

#[tokio::main]
async fn main() {
    let app = create_router();
    let listener = TcpListener::bind("0.0.0.0:48322").await.unwrap();
    println!("Listening on 0.0.0.0:48322");
    axum::serve(listener, app).await.unwrap();
}
```

- [ ] **Step 3: Write health endpoint test**

Create `backend/tests/health_test.rs`:

```rust
use axum::body::Body;
use axum::http::{Request, StatusCode};
use tower::ServiceExt;

#[tokio::test]
async fn health_returns_ok() {
    let app = remote_control_backend::create_router();
    let response = app
        .oneshot(Request::builder().uri("/health").body(Body::empty()).unwrap())
        .await
        .unwrap();
    assert_eq!(response.status(), StatusCode::OK);
}
```

Update `src/main.rs` to make `create_router` public:

```rust
pub fn create_router() -> Router {
    Router::new().route("/health", get(health))
}
```

- [ ] **Step 4: Run test**

Run: `cd backend && cargo test --test health_test -- --nocapture`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
cd backend && git add -A && git commit -m "feat: project scaffold with health endpoint"
```

---

### Task 2: Error Types + Models

**Files:**
- Create: `backend/src/error.rs`
- Create: `backend/src/models.rs`

- [ ] **Step 1: Write error type tests**

Add to `src/error.rs`:

```rust
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use serde::Serialize;

#[derive(Debug)]
pub enum AppError {
    NotFound(String),
    Unauthorized,
    RateLimited,
    BadRequest(String),
    Internal(String),
    TmuxError(String),
}

#[derive(Serialize)]
struct ErrorBody {
    error: String,
    message: String,
    request_id: String,
}

impl IntoResponse for AppError {
    fn into_response(self) -> Response {
        let (status, error, message) = match self {
            AppError::NotFound(msg) => (StatusCode::NOT_FOUND, "not_found", msg),
            AppError::Unauthorized => (StatusCode::UNAUTHORIZED, "unauthorized", "Invalid or missing token".to_string()),
            AppError::RateLimited => (StatusCode::TOO_MANY_REQUESTS, "rate_limited", "Too many requests".to_string()),
            AppError::BadRequest(msg) => (StatusCode::BAD_REQUEST, "bad_request", msg),
            AppError::Internal(msg) => (StatusCode::INTERNAL_SERVER_ERROR, "internal", msg),
            AppError::TmuxError(msg) => (StatusCode::INTERNAL_SERVER_ERROR, "tmux_error", msg),
        };
        let body = ErrorBody {
            error: error.to_string(),
            message,
            request_id: uuid::Uuid::new_v4().to_string(),
        };
        (status, axum::Json(body)).into_response()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use axum::response::IntoResponse;

    #[test]
    fn not_found_returns_404() {
        let err = AppError::NotFound("session not found".into());
        let response = err.into_response();
        assert_eq!(response.status(), StatusCode::NOT_FOUND);
    }

    #[test]
    fn unauthorized_returns_401() {
        let err = AppError::Unauthorized;
        let response = err.into_response();
        assert_eq!(response.status(), StatusCode::UNAUTHORIZED);
    }

    #[test]
    fn rate_limited_returns_429() {
        let err = AppError::RateLimited;
        let response = err.into_response();
        assert_eq!(response.status(), StatusCode::TOO_MANY_REQUESTS);
    }
}
```

- [ ] **Step 2: Run error tests**

Run: `cd backend && cargo test error::tests -- --nocapture`
Expected: PASS (3 tests)

- [ ] **Step 3: Create models**

Create `src/models.rs`:

```rust
use serde::{Deserialize, Serialize};

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct TmuxSession {
    pub id: String,
    pub name: String,
    pub created_at: String,
    pub attached: bool,
}

#[derive(Debug, Serialize, Deserialize, Clone, sqlx::FromRow)]
pub struct Command {
    pub id: String,
    pub name: String,
    pub command: String,
    pub description: Option<String>,
    pub category: String,
    pub icon: Option<String>,
    pub created_at: String,
    pub updated_at: String,
}

#[derive(Debug, Deserialize)]
pub struct CreateCommand {
    pub name: String,
    pub command: String,
    pub description: Option<String>,
    pub category: String,
    pub icon: Option<String>,
}

#[derive(Debug, Deserialize)]
pub struct UpdateCommand {
    pub name: Option<String>,
    pub command: Option<String>,
    pub description: Option<String>,
    pub category: Option<String>,
    pub icon: Option<String>,
}

#[derive(Debug, Deserialize)]
pub struct CreateSession {
    pub name: Option<String>,
}

#[derive(Debug, Deserialize)]
pub struct ExecCommand {
    pub command_id: String,
}
```

- [ ] **Step 4: Register modules in main.rs**

Add to top of `src/main.rs`:

```rust
pub mod error;
pub mod models;
```

- [ ] **Step 5: Run full test suite**

Run: `cd backend && cargo test -- --nocapture`
Expected: PASS (all tests)

- [ ] **Step 6: Commit**

```bash
cd backend && git add -A && git commit -m "feat: add error types and request/response models"
```

---

### Task 3: Configuration

**Files:**
- Create: `backend/config.toml`
- Create: `backend/src/config.rs`

- [ ] **Step 1: Create default config.toml**

```toml
[server]
host = "0.0.0.0"
port = 48322

[auth]
token = ""

[terminal]
scrollback_lines = 10000
default_shell = "/bin/bash"
```

- [ ] **Step 2: Create config.rs with tests**

```rust
use serde::Deserialize;
use rand::Rng;

#[derive(Debug, Deserialize, Clone)]
pub struct Config {
    pub server: ServerConfig,
    pub auth: AuthConfig,
    pub terminal: TerminalConfig,
}

#[derive(Debug, Deserialize, Clone)]
pub struct ServerConfig {
    pub host: String,
    pub port: u16,
}

#[derive(Debug, Deserialize, Clone)]
pub struct AuthConfig {
    pub token: String,
}

#[derive(Debug, Deserialize, Clone)]
pub struct TerminalConfig {
    pub scrollback_lines: u32,
    pub default_shell: String,
}

pub fn generate_token() -> String {
    let mut rng = rand::thread_rng();
    let bytes: Vec<u8> = (0..32).map(|_| rng.gen()).collect();
    hex::encode(bytes)
}

pub fn load_config(path: &str) -> Result<Config, Box<dyn std::error::Error>> {
    let content = std::fs::read_to_string(path)?;
    let mut config: Config = toml::from_str(&content)?;
    if config.auth.token.is_empty() {
        config.auth.token = generate_token();
        let updated = toml::to_string_pretty(&config)?;
        std::fs::write(path, updated)?;
        println!("Generated new auth token: {}", config.auth.token);
    }
    Ok(config)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;

    #[test]
    fn parses_valid_config() {
        let toml = r#"
[server]
host = "127.0.0.1"
port = 9999

[auth]
token = "abc123"

[terminal]
scrollback_lines = 5000
default_shell = "/bin/zsh"
"#;
        let config: Config = toml::from_str(toml).unwrap();
        assert_eq!(config.server.port, 9999);
        assert_eq!(config.auth.token, "abc123");
        assert_eq!(config.terminal.default_shell, "/bin/zsh");
    }

    #[test]
    fn generate_token_returns_64_hex_chars() {
        let token = generate_token();
        assert_eq!(token.len(), 64);
        assert!(token.chars().all(|c| c.is_ascii_hexdigit()));
    }

    #[test]
    fn load_config_generates_token_if_empty() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("config.toml");
        let mut f = std::fs::File::create(&path).unwrap();
        write!(f, r#"
[server]
host = "0.0.0.0"
port = 48322

[auth]
token = ""

[terminal]
scrollback_lines = 10000
default_shell = "/bin/bash"
"#).unwrap();
        let config = load_config(path.to_str().unwrap()).unwrap();
        assert!(!config.auth.token.is_empty());
        assert_eq!(config.auth.token.len(), 64);
    }
}
```

- [ ] **Step 3: Add dependencies for config**

Add to `Cargo.toml` `[dependencies]`:

```toml
hex = "0.4"
```

Add to `[dev-dependencies]`:

```toml
tempfile = "3"
```

Add `serde::Serialize` derive to Config structs (needed for `toml::to_string_pretty`):

```rust
#[derive(Debug, Deserialize, Serialize, Clone)]
pub struct Config { ... }
// (add Serialize to all 4 config structs)
```

- [ ] **Step 4: Register module and run tests**

Add to `src/main.rs`:

```rust
pub mod config;
```

Run: `cd backend && cargo test config::tests -- --nocapture`
Expected: PASS (3 tests)

- [ ] **Step 5: Commit**

```bash
cd backend && git add -A && git commit -m "feat: config loading with auto token generation"
```

---

### Task 4: Database + Migrations

**Files:**
- Create: `backend/migrations/001_initial.sql`
- Create: `backend/src/db.rs`

- [ ] **Step 1: Create migration SQL**

```sql
CREATE TABLE IF NOT EXISTS commands (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    command TEXT NOT NULL,
    description TEXT,
    category TEXT NOT NULL,
    icon TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS config (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL
);
```

- [ ] **Step 2: Create db.rs with init function and test**

```rust
use sqlx::sqlite::{SqlitePool, SqlitePoolOptions};

pub async fn init_pool(database_url: &str) -> Result<SqlitePool, sqlx::Error> {
    let pool = SqlitePoolOptions::new()
        .max_connections(5)
        .connect(database_url)
        .await?;
    run_migrations(&pool).await?;
    Ok(pool)
}

async fn run_migrations(pool: &SqlitePool) -> Result<(), sqlx::Error> {
    sqlx::query(include_str!("../migrations/001_initial.sql"))
        .execute(pool)
        .await?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn migrations_create_tables() {
        let pool = init_pool("sqlite::memory:").await.unwrap();
        let row: (i64,) = sqlx::query_as("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='commands'")
            .fetch_one(&pool)
            .await
            .unwrap();
        assert_eq!(row.0, 1);
    }
}
```

- [ ] **Step 3: Register module and run test**

Add to `src/main.rs`:

```rust
pub mod db;
```

Run: `cd backend && cargo test db::tests -- --nocapture`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
cd backend && git add -A && git commit -m "feat: SQLite database init with migrations"
```

---

### Task 5: AppState + Auth Middleware

**Files:**
- Create: `backend/src/state.rs`
- Create: `backend/src/auth.rs`
- Create: `backend/tests/common/mod.rs`
- Create: `backend/tests/auth_test.rs`

- [ ] **Step 1: Create AppState**

`src/state.rs`:

```rust
use sqlx::sqlite::SqlitePool;
use crate::config::Config;

pub struct AppState {
    pub db: SqlitePool,
    pub config: Config,
}
```

Register in `src/main.rs`:

```rust
pub mod state;
```

- [ ] **Step 2: Write auth extractor**

`src/auth.rs`:

```rust
use axum::extract::FromRequestParts;
use axum::http::request::Parts;
use axum::http::StatusCode;
use std::sync::Arc;
use subtle::ConstantTimeEq;
use crate::error::AppError;
use crate::state::AppState;

pub struct AuthToken;

#[axum::async_trait]
impl FromRequestParts<Arc<AppState>> for AuthToken {
    type Rejection = AppError;

    async fn from_request_parts(parts: &mut Parts, state: &Arc<AppState>) -> Result<Self, Self::Rejection> {
        let header = parts
            .headers
            .get("authorization")
            .and_then(|v| v.to_str().ok())
            .ok_or(AppError::Unauthorized)?;

        let token = header
            .strip_prefix("Bearer ")
            .ok_or(AppError::Unauthorized)?;

        let expected = state.config.auth.token.as_bytes();
        let provided = token.as_bytes();

        if expected.len() != provided.len() || expected.ct_eq(provided).unwrap_u8() != 1 {
            return Err(AppError::Unauthorized);
        }

        Ok(AuthToken)
    }
}
```

Register in `src/main.rs`:

```rust
pub mod auth;
```

- [ ] **Step 3: Create test helpers**

`tests/common/mod.rs`:

```rust
use remote_control_backend::config::{Config, ServerConfig, AuthConfig, TerminalConfig};
use remote_control_backend::state::AppState;
use remote_control_backend::db;
use std::sync::Arc;

pub const TEST_TOKEN: &str = "test-token-abc123";

pub async fn test_state() -> Arc<AppState> {
    let db = db::init_pool("sqlite::memory:").await.unwrap();
    let config = Config {
        server: ServerConfig {
            host: "127.0.0.1".to_string(),
            port: 0,
        },
        auth: AuthConfig {
            token: TEST_TOKEN.to_string(),
        },
        terminal: TerminalConfig {
            scrollback_lines: 1000,
            default_shell: "/bin/bash".to_string(),
        },
    };
    Arc::new(AppState { db, config })
}
```

- [ ] **Step 4: Write auth integration tests**

`tests/auth_test.rs`:

```rust
mod common;

use axum::body::Body;
use axum::http::{Request, StatusCode};
use axum::routing::get;
use axum::Router;
use tower::ServiceExt;
use remote_control_backend::auth::AuthToken;

async fn protected(_: AuthToken) -> &'static str {
    "secret"
}

async fn make_app() -> Router {
    let state = common::test_state().await;
    Router::new()
        .route("/protected", get(protected))
        .with_state(state)
}

#[tokio::test]
async fn valid_token_passes() {
    let app = make_app().await;
    let response = app
        .oneshot(
            Request::builder()
                .uri("/protected")
                .header("authorization", format!("Bearer {}", common::TEST_TOKEN))
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(response.status(), StatusCode::OK);
}

#[tokio::test]
async fn missing_token_returns_401() {
    let app = make_app().await;
    let response = app
        .oneshot(Request::builder().uri("/protected").body(Body::empty()).unwrap())
        .await
        .unwrap();
    assert_eq!(response.status(), StatusCode::UNAUTHORIZED);
}

#[tokio::test]
async fn wrong_token_returns_401() {
    let app = make_app().await;
    let response = app
        .oneshot(
            Request::builder()
                .uri("/protected")
                .header("authorization", "Bearer wrong-token")
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(response.status(), StatusCode::UNAUTHORIZED);
}
```

- [ ] **Step 5: Run auth tests**

Run: `cd backend && cargo test --test auth_test -- --nocapture`
Expected: PASS (3 tests)

- [ ] **Step 6: Commit**

```bash
cd backend && git add -A && git commit -m "feat: bearer token auth middleware with constant-time comparison"
```

---

### Task 6: Rate Limiting

**Files:**
- Create: `backend/src/rate_limit.rs`

- [ ] **Step 1: Create rate limiter with tests**

`src/rate_limit.rs`:

```rust
use std::collections::HashMap;
use std::net::IpAddr;
use std::sync::Mutex;
use std::time::{Duration, Instant};

pub struct RateLimiter {
    state: Mutex<HashMap<IpAddr, IpState>>,
    max_attempts: u32,
    window: Duration,
    block_duration: Duration,
}

struct IpState {
    attempts: u32,
    window_start: Instant,
    blocked_until: Option<Instant>,
    consecutive_failures: u32,
}

impl RateLimiter {
    pub fn new(max_attempts: u32, window_secs: u64, block_secs: u64) -> Self {
        Self {
            state: Mutex::new(HashMap::new()),
            max_attempts,
            window: Duration::from_secs(window_secs),
            block_duration: Duration::from_secs(block_secs),
        }
    }

    pub fn check(&self, ip: IpAddr) -> bool {
        let mut state = self.state.lock().unwrap();
        let now = Instant::now();
        let entry = state.entry(ip).or_insert(IpState {
            attempts: 0,
            window_start: now,
            blocked_until: None,
            consecutive_failures: 0,
        });

        if let Some(blocked_until) = entry.blocked_until {
            if now < blocked_until {
                return false;
            }
            entry.blocked_until = None;
            entry.consecutive_failures = 0;
        }

        if now.duration_since(entry.window_start) > self.window {
            entry.attempts = 0;
            entry.window_start = now;
        }

        entry.attempts += 1;
        entry.attempts <= self.max_attempts
    }

    pub fn record_failure(&self, ip: IpAddr) {
        let mut state = self.state.lock().unwrap();
        if let Some(entry) = state.get_mut(&ip) {
            entry.consecutive_failures += 1;
            if entry.consecutive_failures >= 10 {
                entry.blocked_until = Some(Instant::now() + self.block_duration);
            }
        }
    }

    pub fn reset(&self, ip: IpAddr) {
        let mut state = self.state.lock().unwrap();
        if let Some(entry) = state.get_mut(&ip) {
            entry.consecutive_failures = 0;
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::net::Ipv4Addr;

    fn localhost() -> IpAddr {
        IpAddr::V4(Ipv4Addr::LOCALHOST)
    }

    #[test]
    fn allows_under_limit() {
        let rl = RateLimiter::new(5, 60, 3600);
        for _ in 0..5 {
            assert!(rl.check(localhost()));
        }
    }

    #[test]
    fn blocks_over_limit() {
        let rl = RateLimiter::new(5, 60, 3600);
        for _ in 0..5 {
            rl.check(localhost());
        }
        assert!(!rl.check(localhost()));
    }

    #[test]
    fn blocks_after_10_consecutive_failures() {
        let rl = RateLimiter::new(100, 60, 3600);
        let ip = localhost();
        rl.check(ip);
        for _ in 0..10 {
            rl.record_failure(ip);
        }
        assert!(!rl.check(ip));
    }

    #[test]
    fn reset_clears_failures() {
        let rl = RateLimiter::new(100, 60, 3600);
        let ip = localhost();
        rl.check(ip);
        for _ in 0..5 {
            rl.record_failure(ip);
        }
        rl.reset(ip);
        assert!(rl.check(ip));
    }
}
```

- [ ] **Step 2: Register module and run tests**

Add to `src/main.rs`:

```rust
pub mod rate_limit;
```

Run: `cd backend && cargo test rate_limit::tests -- --nocapture`
Expected: PASS (4 tests)

- [ ] **Step 3: Add RateLimiter to AppState**

Update `src/state.rs`:

```rust
use sqlx::sqlite::SqlitePool;
use crate::config::Config;
use crate::rate_limit::RateLimiter;

pub struct AppState {
    pub db: SqlitePool,
    pub config: Config,
    pub rate_limiter: RateLimiter,
}
```

Update `tests/common/mod.rs` `test_state()`:

```rust
use remote_control_backend::rate_limit::RateLimiter;

pub async fn test_state() -> Arc<AppState> {
    let db = db::init_pool("sqlite::memory:").await.unwrap();
    let config = Config {
        server: ServerConfig { host: "127.0.0.1".to_string(), port: 0 },
        auth: AuthConfig { token: TEST_TOKEN.to_string() },
        terminal: TerminalConfig { scrollback_lines: 1000, default_shell: "/bin/bash".to_string() },
    };
    Arc::new(AppState {
        db,
        config,
        rate_limiter: RateLimiter::new(100, 60, 3600),
    })
}
```

- [ ] **Step 4: Run full test suite**

Run: `cd backend && cargo test -- --nocapture`
Expected: PASS (all tests)

- [ ] **Step 5: Commit**

```bash
cd backend && git add -A && git commit -m "feat: IP-based rate limiter with block after consecutive failures"
```

---

### Task 7: Commands CRUD API

**Files:**
- Create: `backend/src/routes/mod.rs`
- Create: `backend/src/routes/commands.rs`
- Create: `backend/tests/commands_test.rs`

- [ ] **Step 1: Write commands route handlers**

`src/routes/commands.rs`:

```rust
use axum::extract::{Path, State};
use axum::Json;
use std::sync::Arc;
use crate::auth::AuthToken;
use crate::error::AppError;
use crate::models::{Command, CreateCommand, UpdateCommand};
use crate::state::AppState;

pub async fn list(
    _: AuthToken,
    State(state): State<Arc<AppState>>,
) -> Result<Json<Vec<Command>>, AppError> {
    let commands = sqlx::query_as::<_, Command>("SELECT * FROM commands ORDER BY category, name")
        .fetch_all(&state.db)
        .await
        .map_err(|e| AppError::Internal(e.to_string()))?;
    Ok(Json(commands))
}

pub async fn create(
    _: AuthToken,
    State(state): State<Arc<AppState>>,
    Json(input): Json<CreateCommand>,
) -> Result<(axum::http::StatusCode, Json<Command>), AppError> {
    let id = uuid::Uuid::new_v4().to_string();
    let now = chrono::Utc::now().to_rfc3339();
    sqlx::query("INSERT INTO commands (id, name, command, description, category, icon, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")
        .bind(&id)
        .bind(&input.name)
        .bind(&input.command)
        .bind(&input.description)
        .bind(&input.category)
        .bind(&input.icon)
        .bind(&now)
        .bind(&now)
        .execute(&state.db)
        .await
        .map_err(|e| AppError::Internal(e.to_string()))?;
    let command = Command {
        id,
        name: input.name,
        command: input.command,
        description: input.description,
        category: input.category,
        icon: input.icon,
        created_at: now.clone(),
        updated_at: now,
    };
    Ok((axum::http::StatusCode::CREATED, Json(command)))
}

pub async fn update(
    _: AuthToken,
    State(state): State<Arc<AppState>>,
    Path(id): Path<String>,
    Json(input): Json<UpdateCommand>,
) -> Result<Json<Command>, AppError> {
    let existing = sqlx::query_as::<_, Command>("SELECT * FROM commands WHERE id = ?")
        .bind(&id)
        .fetch_optional(&state.db)
        .await
        .map_err(|e| AppError::Internal(e.to_string()))?
        .ok_or_else(|| AppError::NotFound(format!("Command {} not found", id)))?;
    let now = chrono::Utc::now().to_rfc3339();
    let name = input.name.unwrap_or(existing.name);
    let command = input.command.unwrap_or(existing.command);
    let description = input.description.or(existing.description);
    let category = input.category.unwrap_or(existing.category);
    let icon = input.icon.or(existing.icon);
    sqlx::query("UPDATE commands SET name=?, command=?, description=?, category=?, icon=?, updated_at=? WHERE id=?")
        .bind(&name)
        .bind(&command)
        .bind(&description)
        .bind(&category)
        .bind(&icon)
        .bind(&now)
        .bind(&id)
        .execute(&state.db)
        .await
        .map_err(|e| AppError::Internal(e.to_string()))?;
    Ok(Json(Command { id, name, command, description, category, icon, created_at: existing.created_at, updated_at: now }))
}

pub async fn delete(
    _: AuthToken,
    State(state): State<Arc<AppState>>,
    Path(id): Path<String>,
) -> Result<axum::http::StatusCode, AppError> {
    let result = sqlx::query("DELETE FROM commands WHERE id = ?")
        .bind(&id)
        .execute(&state.db)
        .await
        .map_err(|e| AppError::Internal(e.to_string()))?;
    if result.rows_affected() == 0 {
        return Err(AppError::NotFound(format!("Command {} not found", id)));
    }
    Ok(axum::http::StatusCode::NO_CONTENT)
}
```

- [ ] **Step 2: Create routes module**

`src/routes/mod.rs`:

```rust
pub mod commands;

use axum::routing::{get, post, put, delete};
use axum::Router;
use std::sync::Arc;
use crate::state::AppState;

pub fn api_routes() -> Router<Arc<AppState>> {
    Router::new()
        .route("/commands", get(commands::list).post(commands::create))
        .route("/commands/{id}", put(commands::update).delete(commands::delete))
}
```

Register in `src/main.rs`:

```rust
pub mod routes;
```

- [ ] **Step 3: Write commands integration tests**

`tests/commands_test.rs`:

```rust
mod common;

use axum::body::Body;
use axum::http::{Request, StatusCode};
use axum::Router;
use http_body_util::BodyExt;
use tower::ServiceExt;
use remote_control_backend::routes;

async fn make_app() -> Router {
    let state = common::test_state().await;
    routes::api_routes().with_state(state)
}

fn auth_header() -> (&'static str, String) {
    ("authorization", format!("Bearer {}", common::TEST_TOKEN))
}

#[tokio::test]
async fn create_and_list_commands() {
    let app = make_app().await;
    let (key, val) = auth_header();

    let response = app.clone()
        .oneshot(
            Request::builder()
                .method("POST")
                .uri("/commands")
                .header(key, &val)
                .header("content-type", "application/json")
                .body(Body::from(r#"{"name":"Deploy","command":"git push origin main","category":"git"}"#))
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(response.status(), StatusCode::CREATED);
    let body = response.into_body().collect().await.unwrap().to_bytes();
    let created: serde_json::Value = serde_json::from_slice(&body).unwrap();
    assert_eq!(created["name"], "Deploy");

    let response = app
        .oneshot(
            Request::builder()
                .uri("/commands")
                .header(key, &val)
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(response.status(), StatusCode::OK);
    let body = response.into_body().collect().await.unwrap().to_bytes();
    let list: Vec<serde_json::Value> = serde_json::from_slice(&body).unwrap();
    assert_eq!(list.len(), 1);
    assert_eq!(list[0]["name"], "Deploy");
}

#[tokio::test]
async fn update_command() {
    let app = make_app().await;
    let (key, val) = auth_header();

    let response = app.clone()
        .oneshot(
            Request::builder()
                .method("POST")
                .uri("/commands")
                .header(key, &val)
                .header("content-type", "application/json")
                .body(Body::from(r#"{"name":"Old","command":"echo old","category":"test"}"#))
                .unwrap(),
        )
        .await
        .unwrap();
    let body = response.into_body().collect().await.unwrap().to_bytes();
    let created: serde_json::Value = serde_json::from_slice(&body).unwrap();
    let id = created["id"].as_str().unwrap();

    let response = app
        .oneshot(
            Request::builder()
                .method("PUT")
                .uri(&format!("/commands/{}", id))
                .header(key, &val)
                .header("content-type", "application/json")
                .body(Body::from(r#"{"name":"New"}"#))
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(response.status(), StatusCode::OK);
    let body = response.into_body().collect().await.unwrap().to_bytes();
    let updated: serde_json::Value = serde_json::from_slice(&body).unwrap();
    assert_eq!(updated["name"], "New");
    assert_eq!(updated["command"], "echo old");
}

#[tokio::test]
async fn delete_command() {
    let app = make_app().await;
    let (key, val) = auth_header();

    let response = app.clone()
        .oneshot(
            Request::builder()
                .method("POST")
                .uri("/commands")
                .header(key, &val)
                .header("content-type", "application/json")
                .body(Body::from(r#"{"name":"Tmp","command":"echo tmp","category":"test"}"#))
                .unwrap(),
        )
        .await
        .unwrap();
    let body = response.into_body().collect().await.unwrap().to_bytes();
    let created: serde_json::Value = serde_json::from_slice(&body).unwrap();
    let id = created["id"].as_str().unwrap();

    let response = app.clone()
        .oneshot(
            Request::builder()
                .method("DELETE")
                .uri(&format!("/commands/{}", id))
                .header(key, &val)
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(response.status(), StatusCode::NO_CONTENT);

    let response = app
        .oneshot(
            Request::builder()
                .uri("/commands")
                .header(key, &val)
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();
    let body = response.into_body().collect().await.unwrap().to_bytes();
    let list: Vec<serde_json::Value> = serde_json::from_slice(&body).unwrap();
    assert_eq!(list.len(), 0);
}

#[tokio::test]
async fn delete_nonexistent_returns_404() {
    let app = make_app().await;
    let (key, val) = auth_header();

    let response = app
        .oneshot(
            Request::builder()
                .method("DELETE")
                .uri("/commands/nonexistent")
                .header(key, &val)
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(response.status(), StatusCode::NOT_FOUND);
}
```

- [ ] **Step 4: Run commands tests**

Run: `cd backend && cargo test --test commands_test -- --nocapture`
Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
cd backend && git add -A && git commit -m "feat: commands CRUD REST API with full test coverage"
```

---

### Task 8: Tmux Manager

**Files:**
- Create: `backend/src/tmux.rs`

- [ ] **Step 1: Write tmux manager with unit tests for parsing**

`src/tmux.rs`:

```rust
use crate::models::TmuxSession;
use tokio::process::Command;

pub struct TmuxManager;

impl TmuxManager {
    pub fn parse_list_sessions(output: &str) -> Vec<TmuxSession> {
        output
            .lines()
            .filter(|line| !line.is_empty())
            .filter_map(|line| {
                let parts: Vec<&str> = line.splitn(4, ':').collect();
                if parts.len() < 4 {
                    return None;
                }
                Some(TmuxSession {
                    id: parts[0].trim().to_string(),
                    name: parts[1].trim().to_string(),
                    created_at: parts[2].trim().to_string(),
                    attached: parts[3].trim() == "1",
                })
            })
            .collect()
    }

    pub async fn list_sessions() -> Result<Vec<TmuxSession>, String> {
        let output = Command::new("tmux")
            .args(["list-sessions", "-F", "#{session_id}:#{session_name}:#{session_created}:#{session_attached}"])
            .output()
            .await
            .map_err(|e| format!("Failed to run tmux: {}", e))?;

        if !output.status.success() {
            let stderr = String::from_utf8_lossy(&output.stderr);
            if stderr.contains("no server running") || stderr.contains("no sessions") {
                return Ok(vec![]);
            }
            return Err(format!("tmux error: {}", stderr));
        }

        let stdout = String::from_utf8_lossy(&output.stdout);
        Ok(Self::parse_list_sessions(&stdout))
    }

    pub async fn create_session(name: &str, shell: &str) -> Result<TmuxSession, String> {
        let output = Command::new("tmux")
            .args(["new-session", "-d", "-s", name, shell])
            .output()
            .await
            .map_err(|e| format!("Failed to create session: {}", e))?;

        if !output.status.success() {
            return Err(format!("tmux error: {}", String::from_utf8_lossy(&output.stderr)));
        }

        let sessions = Self::list_sessions().await?;
        sessions
            .into_iter()
            .find(|s| s.name == name)
            .ok_or_else(|| "Session created but not found in list".to_string())
    }

    pub async fn kill_session(name: &str) -> Result<(), String> {
        let output = Command::new("tmux")
            .args(["kill-session", "-t", name])
            .output()
            .await
            .map_err(|e| format!("Failed to kill session: {}", e))?;

        if !output.status.success() {
            return Err(format!("tmux error: {}", String::from_utf8_lossy(&output.stderr)));
        }
        Ok(())
    }

    pub async fn send_keys(session: &str, keys: &str) -> Result<(), String> {
        let output = Command::new("tmux")
            .args(["send-keys", "-t", session, keys, "Enter"])
            .output()
            .await
            .map_err(|e| format!("Failed to send keys: {}", e))?;

        if !output.status.success() {
            return Err(format!("tmux error: {}", String::from_utf8_lossy(&output.stderr)));
        }
        Ok(())
    }

    pub async fn session_exists(name: &str) -> bool {
        Command::new("tmux")
            .args(["has-session", "-t", name])
            .output()
            .await
            .map(|o| o.status.success())
            .unwrap_or(false)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parse_list_sessions_valid_output() {
        let output = "$1:main:1711720000:1\n$2:work:1711720100:0\n";
        let sessions = TmuxManager::parse_list_sessions(output);
        assert_eq!(sessions.len(), 2);
        assert_eq!(sessions[0].name, "main");
        assert!(sessions[0].attached);
        assert_eq!(sessions[1].name, "work");
        assert!(!sessions[1].attached);
    }

    #[test]
    fn parse_list_sessions_empty_output() {
        let sessions = TmuxManager::parse_list_sessions("");
        assert!(sessions.is_empty());
    }

    #[test]
    fn parse_list_sessions_malformed_line() {
        let output = "invalid-line\n$1:valid:123:0\n";
        let sessions = TmuxManager::parse_list_sessions(output);
        assert_eq!(sessions.len(), 1);
        assert_eq!(sessions[0].name, "valid");
    }
}
```

- [ ] **Step 2: Register module and run unit tests**

Add to `src/main.rs`:

```rust
pub mod tmux;
```

Run: `cd backend && cargo test tmux::tests -- --nocapture`
Expected: PASS (3 tests)

- [ ] **Step 3: Commit**

```bash
cd backend && git add -A && git commit -m "feat: tmux CLI manager with session listing, creation, and teardown"
```

---

### Task 9: Sessions REST API

**Files:**
- Create: `backend/src/routes/sessions.rs`
- Modify: `backend/src/routes/mod.rs`

- [ ] **Step 1: Write session route handlers**

`src/routes/sessions.rs`:

```rust
use axum::extract::{Path, State};
use axum::Json;
use std::sync::Arc;
use crate::auth::AuthToken;
use crate::error::AppError;
use crate::models::{CreateSession, ExecCommand, TmuxSession};
use crate::state::AppState;
use crate::tmux::TmuxManager;

pub async fn list(
    _: AuthToken,
    State(_state): State<Arc<AppState>>,
) -> Result<Json<Vec<TmuxSession>>, AppError> {
    let sessions = TmuxManager::list_sessions()
        .await
        .map_err(AppError::TmuxError)?;
    Ok(Json(sessions))
}

pub async fn create(
    _: AuthToken,
    State(state): State<Arc<AppState>>,
    Json(input): Json<CreateSession>,
) -> Result<(axum::http::StatusCode, Json<TmuxSession>), AppError> {
    let name = input
        .name
        .unwrap_or_else(|| format!("rc-{}", uuid::Uuid::new_v4().to_string().split('-').next().unwrap()));
    let session = TmuxManager::create_session(&name, &state.config.terminal.default_shell)
        .await
        .map_err(AppError::TmuxError)?;
    Ok((axum::http::StatusCode::CREATED, Json(session)))
}

pub async fn delete(
    _: AuthToken,
    State(_state): State<Arc<AppState>>,
    Path(name): Path<String>,
) -> Result<axum::http::StatusCode, AppError> {
    if !TmuxManager::session_exists(&name).await {
        return Err(AppError::NotFound(format!("Session {} not found", name)));
    }
    TmuxManager::kill_session(&name)
        .await
        .map_err(AppError::TmuxError)?;
    Ok(axum::http::StatusCode::NO_CONTENT)
}

pub async fn status(
    _: AuthToken,
    State(_state): State<Arc<AppState>>,
    Path(name): Path<String>,
) -> Result<Json<TmuxSession>, AppError> {
    let sessions = TmuxManager::list_sessions()
        .await
        .map_err(AppError::TmuxError)?;
    let session = sessions
        .into_iter()
        .find(|s| s.name == name)
        .ok_or_else(|| AppError::NotFound(format!("Session {} not found", name)))?;
    Ok(Json(session))
}

pub async fn exec(
    _: AuthToken,
    State(state): State<Arc<AppState>>,
    Path(name): Path<String>,
    Json(input): Json<ExecCommand>,
) -> Result<axum::http::StatusCode, AppError> {
    if !TmuxManager::session_exists(&name).await {
        return Err(AppError::NotFound(format!("Session {} not found", name)));
    }
    let cmd = sqlx::query_as::<_, crate::models::Command>("SELECT * FROM commands WHERE id = ?")
        .bind(&input.command_id)
        .fetch_optional(&state.db)
        .await
        .map_err(|e| AppError::Internal(e.to_string()))?
        .ok_or_else(|| AppError::NotFound(format!("Command {} not found", input.command_id)))?;
    TmuxManager::send_keys(&name, &cmd.command)
        .await
        .map_err(AppError::TmuxError)?;
    Ok(axum::http::StatusCode::OK)
}
```

- [ ] **Step 2: Add session routes to router**

Update `src/routes/mod.rs`:

```rust
pub mod commands;
pub mod sessions;

use axum::routing::{get, post, put, delete};
use axum::Router;
use std::sync::Arc;
use crate::state::AppState;

pub fn api_routes() -> Router<Arc<AppState>> {
    Router::new()
        .route("/commands", get(commands::list).post(commands::create))
        .route("/commands/{id}", put(commands::update).delete(commands::delete))
        .route("/sessions", get(sessions::list).post(sessions::create))
        .route("/sessions/{id}", delete(sessions::delete))
        .route("/sessions/{id}/status", get(sessions::status))
        .route("/sessions/{id}/exec", post(sessions::exec))
}
```

- [ ] **Step 3: Verify compilation**

Run: `cd backend && cargo build`
Expected: compiles successfully

- [ ] **Step 4: Commit**

```bash
cd backend && git add -A && git commit -m "feat: sessions REST API (list, create, delete, status, exec)"
```

---

### Task 10: WebSocket Binary Protocol

**Files:**
- Create: `backend/src/protocol.rs`
- Create: `backend/tests/protocol_test.rs`

- [ ] **Step 1: Write protocol encode/decode with tests**

`src/protocol.rs`:

```rust
use serde::{Deserialize, Serialize};

const TYPE_DATA: u8 = 0x00;
const TYPE_RESIZE: u8 = 0x01;
const TYPE_PING: u8 = 0x02;
const TYPE_PONG: u8 = 0x03;
const TYPE_SESSION_EVENT: u8 = 0x04;

#[derive(Debug, PartialEq)]
pub enum Frame {
    Data(Vec<u8>),
    Resize { cols: u16, rows: u16 },
    Ping,
    Pong,
    SessionEvent(SessionEventPayload),
}

#[derive(Debug, Serialize, Deserialize, PartialEq)]
pub struct SessionEventPayload {
    pub event_type: String,
    pub session_id: String,
}

#[derive(Debug, Deserialize)]
struct ResizePayload {
    cols: u16,
    rows: u16,
}

impl Frame {
    pub fn encode(&self) -> Vec<u8> {
        match self {
            Frame::Data(data) => {
                let mut buf = Vec::with_capacity(1 + data.len());
                buf.push(TYPE_DATA);
                buf.extend_from_slice(data);
                buf
            }
            Frame::Resize { cols, rows } => {
                let mut buf = Vec::with_capacity(5);
                buf.push(TYPE_RESIZE);
                buf.extend_from_slice(&cols.to_be_bytes());
                buf.extend_from_slice(&rows.to_be_bytes());
                buf
            }
            Frame::Ping => vec![TYPE_PING],
            Frame::Pong => vec![TYPE_PONG],
            Frame::SessionEvent(payload) => {
                let json = serde_json::to_vec(payload).unwrap();
                let mut buf = Vec::with_capacity(1 + json.len());
                buf.push(TYPE_SESSION_EVENT);
                buf.extend_from_slice(&json);
                buf
            }
        }
    }

    pub fn decode(data: &[u8]) -> Result<Self, String> {
        if data.is_empty() {
            return Err("Empty frame".to_string());
        }
        match data[0] {
            TYPE_DATA => Ok(Frame::Data(data[1..].to_vec())),
            TYPE_RESIZE => {
                if data.len() < 5 {
                    return Err("Resize frame too short".to_string());
                }
                let cols = u16::from_be_bytes([data[1], data[2]]);
                let rows = u16::from_be_bytes([data[3], data[4]]);
                Ok(Frame::Resize { cols, rows })
            }
            TYPE_PING => Ok(Frame::Ping),
            TYPE_PONG => Ok(Frame::Pong),
            TYPE_SESSION_EVENT => {
                let payload: SessionEventPayload = serde_json::from_slice(&data[1..])
                    .map_err(|e| format!("Invalid session event: {}", e))?;
                Ok(Frame::SessionEvent(payload))
            }
            t => Err(format!("Unknown frame type: 0x{:02x}", t)),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn data_frame_roundtrip() {
        let frame = Frame::Data(b"hello terminal".to_vec());
        let encoded = frame.encode();
        assert_eq!(encoded[0], TYPE_DATA);
        let decoded = Frame::decode(&encoded).unwrap();
        assert_eq!(decoded, frame);
    }

    #[test]
    fn resize_frame_roundtrip() {
        let frame = Frame::Resize { cols: 120, rows: 40 };
        let encoded = frame.encode();
        assert_eq!(encoded[0], TYPE_RESIZE);
        assert_eq!(encoded.len(), 5);
        let decoded = Frame::decode(&encoded).unwrap();
        assert_eq!(decoded, frame);
    }

    #[test]
    fn ping_pong_roundtrip() {
        let ping = Frame::Ping;
        assert_eq!(Frame::decode(&ping.encode()).unwrap(), Frame::Ping);
        let pong = Frame::Pong;
        assert_eq!(Frame::decode(&pong.encode()).unwrap(), Frame::Pong);
    }

    #[test]
    fn session_event_roundtrip() {
        let frame = Frame::SessionEvent(SessionEventPayload {
            event_type: "ended".to_string(),
            session_id: "rc-abc123".to_string(),
        });
        let encoded = frame.encode();
        assert_eq!(encoded[0], TYPE_SESSION_EVENT);
        let decoded = Frame::decode(&encoded).unwrap();
        assert_eq!(decoded, frame);
    }

    #[test]
    fn empty_frame_returns_error() {
        assert!(Frame::decode(&[]).is_err());
    }

    #[test]
    fn unknown_type_returns_error() {
        assert!(Frame::decode(&[0xFF]).is_err());
    }

    #[test]
    fn resize_too_short_returns_error() {
        assert!(Frame::decode(&[TYPE_RESIZE, 0x00]).is_err());
    }
}
```

- [ ] **Step 2: Register module and run tests**

Add to `src/main.rs`:

```rust
pub mod protocol;
```

Run: `cd backend && cargo test protocol::tests -- --nocapture`
Expected: PASS (7 tests)

- [ ] **Step 3: Commit**

```bash
cd backend && git add -A && git commit -m "feat: binary WebSocket protocol with frame encode/decode"
```

---

### Task 11: Terminal WebSocket Handler

**Files:**
- Create: `backend/src/routes/terminal.rs`
- Modify: `backend/src/routes/mod.rs`

- [ ] **Step 1: Write terminal WebSocket handler**

`src/routes/terminal.rs`:

```rust
use axum::extract::ws::{Message, WebSocket, WebSocketUpgrade};
use axum::extract::{Path, Query, State};
use axum::response::IntoResponse;
use portable_pty::{CommandBuilder, NativePtySystem, PtySize, PtySystem};
use std::sync::Arc;
use tokio::sync::mpsc;
use crate::error::AppError;
use crate::protocol::{Frame, SessionEventPayload};
use crate::state::AppState;
use crate::tmux::TmuxManager;

#[derive(serde::Deserialize)]
pub struct WsQuery {
    token: String,
}

pub async fn ws_handler(
    ws: WebSocketUpgrade,
    State(state): State<Arc<AppState>>,
    Path(session_id): Path<String>,
    Query(query): Query<WsQuery>,
) -> Result<impl IntoResponse, AppError> {
    if query.token != state.config.auth.token {
        return Err(AppError::Unauthorized);
    }
    if !TmuxManager::session_exists(&session_id).await {
        return Err(AppError::NotFound(format!("Session {} not found", session_id)));
    }
    Ok(ws.on_upgrade(move |socket| handle_socket(socket, session_id)))
}

async fn handle_socket(mut socket: WebSocket, session_id: String) {
    let pty_system = NativePtySystem::default();
    let size = PtySize {
        rows: 24,
        cols: 80,
        pixel_width: 0,
        pixel_height: 0,
    };

    let pair = match pty_system.openpty(size) {
        Ok(p) => p,
        Err(e) => {
            let frame = Frame::SessionEvent(SessionEventPayload {
                event_type: "error".to_string(),
                session_id: session_id.clone(),
            });
            let _ = socket.send(Message::Binary(frame.encode().into())).await;
            return;
        }
    };

    let mut cmd = CommandBuilder::new("tmux");
    cmd.arg("attach-session");
    cmd.arg("-t");
    cmd.arg(&session_id);

    let _child = match pair.slave.spawn_command(cmd) {
        Ok(c) => c,
        Err(e) => {
            let frame = Frame::SessionEvent(SessionEventPayload {
                event_type: "error".to_string(),
                session_id: session_id.clone(),
            });
            let _ = socket.send(Message::Binary(frame.encode().into())).await;
            return;
        }
    };
    drop(pair.slave);

    let reader = match pair.master.try_clone_reader() {
        Ok(r) => r,
        Err(_) => return,
    };
    let writer = pair.master.take_writer().unwrap();
    let master = Arc::new(tokio::sync::Mutex::new(pair.master));

    let (pty_tx, mut pty_rx) = mpsc::channel::<Vec<u8>>(256);
    let (ws_tx, mut ws_rx) = mpsc::channel::<Vec<u8>>(256);

    let read_handle = tokio::task::spawn_blocking(move || {
        let mut reader = reader;
        let mut buf = [0u8; 4096];
        loop {
            match std::io::Read::read(&mut reader, &mut buf) {
                Ok(0) => break,
                Ok(n) => {
                    if pty_tx.blocking_send(buf[..n].to_vec()).is_err() {
                        break;
                    }
                }
                Err(_) => break,
            }
        }
    });

    let write_handle = tokio::task::spawn_blocking(move || {
        let mut writer = writer;
        while let Some(data) = ws_rx.blocking_recv() {
            if std::io::Write::write_all(&mut writer, &data).is_err() {
                break;
            }
        }
    });

    loop {
        tokio::select! {
            Some(data) = pty_rx.recv() => {
                let frame = Frame::Data(data);
                if socket.send(Message::Binary(frame.encode().into())).await.is_err() {
                    break;
                }
            }
            msg = socket.recv() => {
                match msg {
                    Some(Ok(Message::Binary(data))) => {
                        match Frame::decode(&data) {
                            Ok(Frame::Data(input)) => {
                                if ws_tx.send(input).await.is_err() {
                                    break;
                                }
                            }
                            Ok(Frame::Resize { cols, rows }) => {
                                let m = master.lock().await;
                                let _ = m.resize(PtySize {
                                    rows,
                                    cols,
                                    pixel_width: 0,
                                    pixel_height: 0,
                                });
                            }
                            Ok(Frame::Ping) => {
                                let pong = Frame::Pong;
                                let _ = socket.send(Message::Binary(pong.encode().into())).await;
                            }
                            _ => {}
                        }
                    }
                    Some(Ok(Message::Close(_))) | None => break,
                    _ => {}
                }
            }
        }
    }

    drop(ws_tx);
    read_handle.abort();
    let _ = write_handle.await;

    let event = Frame::SessionEvent(SessionEventPayload {
        event_type: "detached".to_string(),
        session_id,
    });
    let _ = socket.send(Message::Binary(event.encode().into())).await;
    let _ = socket.close().await;
}
```

- [ ] **Step 2: Add terminal route to router**

Update `src/routes/mod.rs`:

```rust
pub mod commands;
pub mod sessions;
pub mod terminal;

use axum::routing::{get, post, put, delete};
use axum::Router;
use std::sync::Arc;
use crate::state::AppState;

pub fn api_routes() -> Router<Arc<AppState>> {
    Router::new()
        .route("/commands", get(commands::list).post(commands::create))
        .route("/commands/{id}", put(commands::update).delete(commands::delete))
        .route("/sessions", get(sessions::list).post(sessions::create))
        .route("/sessions/{id}", delete(sessions::delete))
        .route("/sessions/{id}/status", get(sessions::status))
        .route("/sessions/{id}/exec", post(sessions::exec))
        .route("/sessions/{id}/terminal", get(terminal::ws_handler))
}
```

- [ ] **Step 3: Verify compilation**

Run: `cd backend && cargo build`
Expected: compiles successfully

- [ ] **Step 4: Commit**

```bash
cd backend && git add -A && git commit -m "feat: WebSocket terminal handler with PTY bridge to tmux sessions"
```

---

### Task 12: Wire Main + Startup

**Files:**
- Modify: `backend/src/main.rs`

- [ ] **Step 1: Complete main.rs with full startup**

Replace `src/main.rs` entirely:

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

use std::sync::Arc;
use tokio::net::TcpListener;
use tower_http::cors::CorsLayer;
use tower_http::trace::TraceLayer;
use tracing_subscriber::EnvFilter;
use crate::rate_limit::RateLimiter;
use crate::state::AppState;

async fn health() -> &'static str {
    "ok"
}

pub fn create_router() -> axum::Router<Arc<AppState>> {
    axum::Router::new()
        .route("/health", axum::routing::get(health))
        .merge(routes::api_routes())
        .layer(CorsLayer::permissive())
        .layer(TraceLayer::new_for_http())
}

#[tokio::main]
async fn main() {
    tracing_subscriber::fmt()
        .with_env_filter(EnvFilter::from_default_env().add_directive("info".parse().unwrap()))
        .json()
        .init();

    let config = config::load_config("config.toml").expect("Failed to load config");
    let db = db::init_pool("sqlite:data.db?mode=rwc")
        .await
        .expect("Failed to init database");
    let rate_limiter = RateLimiter::new(5, 60, 3600);

    let state = Arc::new(AppState {
        db,
        config: config.clone(),
        rate_limiter,
    });

    let app = create_router().with_state(state);
    let addr = format!("{}:{}", config.server.host, config.server.port);
    let listener = TcpListener::bind(&addr).await.unwrap();
    tracing::info!("Listening on {}", addr);
    axum::serve(listener, app).await.unwrap();
}
```

- [ ] **Step 2: Update health test for new Router type**

Update `tests/health_test.rs`:

```rust
mod common;

use axum::body::Body;
use axum::http::{Request, StatusCode};
use tower::ServiceExt;

#[tokio::test]
async fn health_returns_ok() {
    let state = common::test_state().await;
    let app = remote_control_backend::create_router().with_state(state);
    let response = app
        .oneshot(Request::builder().uri("/health").body(Body::empty()).unwrap())
        .await
        .unwrap();
    assert_eq!(response.status(), StatusCode::OK);
}
```

- [ ] **Step 3: Run full test suite**

Run: `cd backend && cargo test -- --nocapture`
Expected: PASS (all tests)

- [ ] **Step 4: Add .gitignore**

Create `backend/.gitignore`:

```
/target
data.db
data.db-*
```

- [ ] **Step 5: Commit**

```bash
cd backend && git add -A && git commit -m "feat: complete backend wiring with startup, logging, and CORS"
```

---

## Summary

| Task | Component | Tests |
|------|-----------|-------|
| 1 | Project scaffold + health | 1 integration |
| 2 | Error types + models | 3 unit |
| 3 | Config loading | 3 unit |
| 4 | Database + migrations | 1 unit |
| 5 | Auth middleware | 3 integration |
| 6 | Rate limiting | 4 unit |
| 7 | Commands CRUD | 4 integration |
| 8 | Tmux manager | 3 unit (parsing) |
| 9 | Sessions API | compile check |
| 10 | WebSocket protocol | 7 unit |
| 11 | Terminal WebSocket | compile check |
| 12 | Main wiring | 1 integration |

**Total: 12 tasks, ~55 steps, 30 tests**
