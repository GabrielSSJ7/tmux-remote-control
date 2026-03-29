use remote_control_backend::config::{Config, ServerConfig, AuthConfig, TerminalConfig};
use remote_control_backend::rate_limit::RateLimiter;
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
    Arc::new(AppState { db, config, rate_limiter: RateLimiter::new(100, 60, 3600) })
}
