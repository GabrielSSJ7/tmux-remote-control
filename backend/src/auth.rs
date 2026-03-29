use axum::extract::FromRequestParts;
use axum::http::request::Parts;
use std::sync::Arc;
use subtle::ConstantTimeEq;
use crate::error::AppError;
use crate::state::AppState;

pub struct AuthToken;

#[axum::async_trait]
impl FromRequestParts<Arc<AppState>> for AuthToken {
    type Rejection = AppError;

    async fn from_request_parts(parts: &mut Parts, state: &Arc<AppState>) -> Result<Self, Self::Rejection> {
        let header = parts
            .headers
            .get("authorization")
            .and_then(|v| v.to_str().ok())
            .ok_or(AppError::Unauthorized)?;

        let token = header
            .strip_prefix("Bearer ")
            .ok_or(AppError::Unauthorized)?;

        let expected = state.config.auth.token.as_bytes();
        let provided = token.as_bytes();

        if expected.len() != provided.len() || expected.ct_eq(provided).unwrap_u8() != 1 {
            return Err(AppError::Unauthorized);
        }

        Ok(AuthToken)
    }
}
