pub mod auth;
pub mod config;
pub mod db;
pub mod error;
pub mod models;
pub mod rate_limit;
pub mod routes;
pub mod state;
pub mod tmux;

use axum::{routing::get, Router};

async fn health() -> &'static str {
    "ok"
}

/// Constructs the application router with all registered routes.
pub fn create_router() -> Router {
    Router::new().route("/health", get(health))
}
