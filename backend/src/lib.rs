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
use axum::http::{HeaderValue, Method, header};
use tower_http::cors::{CorsLayer, AllowOrigin};
use state::AppState;

async fn health() -> &'static str {
    "ok"
}

/// Constructs the application router with an explicit CORS origin allowlist.
///
/// If `allowed_origins` is empty, all cross-origin requests are rejected and
/// a warning is logged.
pub fn create_router(allowed_origins: &[String]) -> Router<Arc<AppState>> {
    if allowed_origins.is_empty() {
        tracing::warn!("allowed_origins is empty -- all cross-origin requests will be rejected");
    }

    let origins: Vec<HeaderValue> = allowed_origins
        .iter()
        .filter_map(|o| o.parse().ok())
        .collect();

    let cors = CorsLayer::new()
        .allow_origin(AllowOrigin::list(origins))
        .allow_methods([Method::GET, Method::POST, Method::PUT, Method::DELETE])
        .allow_headers([header::CONTENT_TYPE, header::AUTHORIZATION]);

    Router::new()
        .route("/health", get(health))
        .merge(routes::api_routes())
        .layer(cors)
        .layer(tower_http::trace::TraceLayer::new_for_http())
}
