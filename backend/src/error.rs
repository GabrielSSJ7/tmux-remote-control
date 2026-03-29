use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use serde::Serialize;

#[derive(Debug)]
pub enum AppError {
    NotFound(String),
    Unauthorized,
    RateLimited,
    BadRequest(String),
    Internal(String),
    TmuxError(String),
}

#[derive(Serialize)]
struct ErrorBody {
    error: String,
    message: String,
    request_id: String,
}

impl IntoResponse for AppError {
    fn into_response(self) -> Response {
        let (status, error, message) = match self {
            AppError::NotFound(msg) => (StatusCode::NOT_FOUND, "not_found", msg),
            AppError::Unauthorized => (StatusCode::UNAUTHORIZED, "unauthorized", "Invalid or missing token".to_string()),
            AppError::RateLimited => (StatusCode::TOO_MANY_REQUESTS, "rate_limited", "Too many requests".to_string()),
            AppError::BadRequest(msg) => (StatusCode::BAD_REQUEST, "bad_request", msg),
            AppError::Internal(msg) => (StatusCode::INTERNAL_SERVER_ERROR, "internal", msg),
            AppError::TmuxError(msg) => (StatusCode::INTERNAL_SERVER_ERROR, "tmux_error", msg),
        };
        let body = ErrorBody {
            error: error.to_string(),
            message,
            request_id: uuid::Uuid::new_v4().to_string(),
        };
        (status, axum::Json(body)).into_response()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use axum::response::IntoResponse;

    #[test]
    fn not_found_returns_404() {
        let err = AppError::NotFound("session not found".into());
        let response = err.into_response();
        assert_eq!(response.status(), StatusCode::NOT_FOUND);
    }

    #[test]
    fn unauthorized_returns_401() {
        let err = AppError::Unauthorized;
        let response = err.into_response();
        assert_eq!(response.status(), StatusCode::UNAUTHORIZED);
    }

    #[test]
    fn rate_limited_returns_429() {
        let err = AppError::RateLimited;
        let response = err.into_response();
        assert_eq!(response.status(), StatusCode::TOO_MANY_REQUESTS);
    }
}
