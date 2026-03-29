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
use axum::{routing::get, Router};
use state::AppState;

async fn health() -> &'static str {
    "ok"
}

/// Constructs the application router with all registered routes.
pub fn create_router() -> Router<Arc<AppState>> {
    Router::new()
        .route("/health", get(health))
        .merge(routes::api_routes())
        .layer(tower_http::cors::CorsLayer::permissive())
        .layer(tower_http::trace::TraceLayer::new_for_http())
}
