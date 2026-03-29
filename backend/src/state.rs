use sqlx::sqlite::SqlitePool;
use crate::config::Config;
use crate::rate_limit::RateLimiter;

pub struct AppState {
    pub db: SqlitePool,
    pub config: Config,
    pub rate_limiter: RateLimiter,
}
