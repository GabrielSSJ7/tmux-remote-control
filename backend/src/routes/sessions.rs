use axum::extract::{Path, State};
use axum::Json;
use std::sync::Arc;
use crate::auth::AuthToken;
use crate::error::AppError;
use crate::models::{CreateSession, ExecCommand, TmuxSession};
use crate::state::AppState;
use crate::tmux::TmuxManager;

/// Lists all active tmux sessions.
pub async fn list(
    _: AuthToken,
    State(_state): State<Arc<AppState>>,
) -> Result<Json<Vec<TmuxSession>>, AppError> {
    let sessions = TmuxManager::list_sessions()
        .await
        .map_err(AppError::TmuxError)?;
    Ok(Json(sessions))
}

/// Creates a new tmux session with an optional name.
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

/// Deletes a tmux session by name.
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

/// Returns the status of a specific tmux session by name.
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

/// Executes a stored command in the specified tmux session.
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
