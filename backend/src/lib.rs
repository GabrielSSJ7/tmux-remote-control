pub mod config;
pub mod db;
pub mod error;
pub mod models;

use axum::{routing::get, Router};

async fn health() -> &'static str {
    "ok"
}

/// Constructs the application router with all registered routes.
pub fn create_router() -> Router {
    Router::new().route("/health", get(health))
}
