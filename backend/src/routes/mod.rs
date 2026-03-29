pub mod commands;
pub mod sessions;
pub mod terminal;

use axum::routing::{get, post, delete};
use axum::Router;
use std::sync::Arc;
use crate::state::AppState;

/// Constructs the API router with all command and session routes.
pub fn api_routes() -> Router<Arc<AppState>> {
    Router::new()
        .route("/commands", get(commands::list).post(commands::create))
        .route("/commands/:id", axum::routing::put(commands::update).delete(commands::delete))
        .route("/sessions", get(sessions::list).post(sessions::create))
        .route("/sessions/:id", delete(sessions::delete))
        .route("/sessions/:id/status", get(sessions::status))
        .route("/sessions/:id/exec", post(sessions::exec))
        .route("/sessions/:id/terminal", get(terminal::ws_handler))
}
