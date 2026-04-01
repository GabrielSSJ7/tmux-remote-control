use remote_control_backend::config::{Config, ServerConfig, AuthConfig, TerminalConfig};
use remote_control_backend::rate_limit::RateLimiter;
use remote_control_backend::state::AppState;
use sqlx::sqlite::SqlitePoolOptions;
use std::sync::Arc;

pub const TEST_TOKEN: &str = "test-token-abc123";

async fn init_test_pool() -> sqlx::SqlitePool {
    let pool = SqlitePoolOptions::new()
        .max_connections(1)
        .connect("sqlite::memory:")
        .await
        .unwrap();
    let sql = include_str!("../../migrations/001_initial.sql");
    for statement in sql.split(';').map(str::trim).filter(|s: &&str| !s.is_empty()) {
        sqlx::query(statement).execute(&pool).await.unwrap();
    }
    pool
}

pub async fn test_state() -> Arc<AppState> {
    let db = init_test_pool().await;
    let config = Config {
        server: ServerConfig {
            host: "127.0.0.1".to_string(),
            port: 0,
            allowed_origins: vec!["http://localhost".to_string()],
        },
        auth: AuthConfig {
            token: TEST_TOKEN.to_string(),
        },
        terminal: TerminalConfig {
            scrollback_lines: 1000,
            default_shell: "/bin/bash".to_string(),
        },
    };
    Arc::new(AppState { db, config, rate_limiter: RateLimiter::new(100, 60, 3600) })
}
