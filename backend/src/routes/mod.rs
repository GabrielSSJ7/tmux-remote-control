pub mod commands;

use axum::routing::{get, put};
use axum::Router;
use std::sync::Arc;
use crate::state::AppState;

/// Constructs the API router with all command and session routes.
pub fn api_routes() -> Router<Arc<AppState>> {
    Router::new()
        .route("/commands", get(commands::list).post(commands::create))
        .route("/commands/:id", put(commands::update).delete(commands::delete))
}
