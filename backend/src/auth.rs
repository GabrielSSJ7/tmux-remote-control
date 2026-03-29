use axum::extract::FromRequestParts;
use axum::http::request::Parts;
use std::net::SocketAddr;
use std::sync::Arc;
use subtle::ConstantTimeEq;
use crate::error::AppError;
use crate::state::AppState;

pub struct AuthToken;

#[axum::async_trait]
impl FromRequestParts<Arc<AppState>> for AuthToken {
    type Rejection = AppError;

    async fn from_request_parts(parts: &mut Parts, state: &Arc<AppState>) -> Result<Self, Self::Rejection> {
        let ip = parts
            .extensions
            .get::<axum::extract::ConnectInfo<SocketAddr>>()
            .map(|ci| ci.0.ip())
            .unwrap_or_else(|| std::net::IpAddr::V4(std::net::Ipv4Addr::UNSPECIFIED));

        if !state.rate_limiter.check(ip) {
            return Err(AppError::RateLimited);
        }

        let header = parts
            .headers
            .get("authorization")
            .and_then(|v| v.to_str().ok())
            .ok_or_else(|| {
                state.rate_limiter.record_failure(ip);
                AppError::Unauthorized
            })?;

        let token = header
            .strip_prefix("Bearer ")
            .ok_or_else(|| {
                state.rate_limiter.record_failure(ip);
                AppError::Unauthorized
            })?;

        let expected = state.config.auth.token.as_bytes();
        let provided = token.as_bytes();

        if expected.len() != provided.len() || expected.ct_eq(provided).unwrap_u8() != 1 {
            state.rate_limiter.record_failure(ip);
            return Err(AppError::Unauthorized);
        }

        state.rate_limiter.reset(ip);
        Ok(AuthToken)
    }
}
