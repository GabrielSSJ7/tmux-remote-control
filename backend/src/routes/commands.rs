use axum::extract::{Path, State};
use axum::Json;
use std::sync::Arc;
use crate::auth::AuthToken;
use crate::error::AppError;
use crate::models::{Command, CreateCommand, UpdateCommand};
use crate::state::AppState;

/// Lists all commands ordered by category and name.
pub async fn list(
    _: AuthToken,
    State(state): State<Arc<AppState>>,
) -> Result<Json<Vec<Command>>, AppError> {
    let commands = sqlx::query_as::<_, Command>("SELECT * FROM commands ORDER BY category, name")
        .fetch_all(&state.db)
        .await
        .map_err(|e| {
            tracing::error!("Database error: {}", e);
            AppError::Internal("Database error".to_string())
        })?;
    Ok(Json(commands))
}

/// Creates a new command and returns it with HTTP 201.
pub async fn create(
    _: AuthToken,
    State(state): State<Arc<AppState>>,
    Json(input): Json<CreateCommand>,
) -> Result<(axum::http::StatusCode, Json<Command>), AppError> {
    let id = uuid::Uuid::new_v4().to_string();
    let now = chrono::Utc::now().to_rfc3339();
    sqlx::query("INSERT INTO commands (id, name, command, description, category, icon, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")
        .bind(&id)
        .bind(&input.name)
        .bind(&input.command)
        .bind(&input.description)
        .bind(&input.category)
        .bind(&input.icon)
        .bind(&now)
        .bind(&now)
        .execute(&state.db)
        .await
        .map_err(|e| {
            tracing::error!("Database error: {}", e);
            AppError::Internal("Database error".to_string())
        })?;
    let command = Command {
        id,
        name: input.name,
        command: input.command,
        description: input.description,
        category: input.category,
        icon: input.icon,
        created_at: now.clone(),
        updated_at: now,
    };
    Ok((axum::http::StatusCode::CREATED, Json(command)))
}

/// Updates an existing command by ID, merging provided fields with existing values.
pub async fn update(
    _: AuthToken,
    State(state): State<Arc<AppState>>,
    Path(id): Path<String>,
    Json(input): Json<UpdateCommand>,
) -> Result<Json<Command>, AppError> {
    let existing = sqlx::query_as::<_, Command>("SELECT * FROM commands WHERE id = ?")
        .bind(&id)
        .fetch_optional(&state.db)
        .await
        .map_err(|e| {
            tracing::error!("Database error: {}", e);
            AppError::Internal("Database error".to_string())
        })?
        .ok_or_else(|| AppError::NotFound(format!("Command {} not found", id)))?;
    let now = chrono::Utc::now().to_rfc3339();
    let name = input.name.unwrap_or(existing.name);
    let command = input.command.unwrap_or(existing.command);
    let description = input.description.or(existing.description);
    let category = input.category.unwrap_or(existing.category);
    let icon = input.icon.or(existing.icon);
    sqlx::query("UPDATE commands SET name=?, command=?, description=?, category=?, icon=?, updated_at=? WHERE id=?")
        .bind(&name)
        .bind(&command)
        .bind(&description)
        .bind(&category)
        .bind(&icon)
        .bind(&now)
        .bind(&id)
        .execute(&state.db)
        .await
        .map_err(|e| {
            tracing::error!("Database error: {}", e);
            AppError::Internal("Database error".to_string())
        })?;
    Ok(Json(Command { id, name, command, description, category, icon, created_at: existing.created_at, updated_at: now }))
}

/// Deletes a command by ID, returning 204 on success or 404 if not found.
pub async fn delete(
    _: AuthToken,
    State(state): State<Arc<AppState>>,
    Path(id): Path<String>,
) -> Result<axum::http::StatusCode, AppError> {
    let result = sqlx::query("DELETE FROM commands WHERE id = ?")
        .bind(&id)
        .execute(&state.db)
        .await
        .map_err(|e| {
            tracing::error!("Database error: {}", e);
            AppError::Internal("Database error".to_string())
        })?;
    if result.rows_affected() == 0 {
        return Err(AppError::NotFound(format!("Command {} not found", id)));
    }
    Ok(axum::http::StatusCode::NO_CONTENT)
}
