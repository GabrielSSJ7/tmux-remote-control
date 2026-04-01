mod common;

use axum::body::Body;
use axum::http::{Request, StatusCode};
use tower::ServiceExt;

#[tokio::test]
async fn health_returns_ok() {
    let state = common::test_state().await;
    let allowed_origins = state.config.server.allowed_origins.clone();
    let app = remote_control_backend::create_router(&allowed_origins).with_state(state);
    let response = app
        .oneshot(Request::builder().uri("/health").body(Body::empty()).unwrap())
        .await
        .unwrap();
    assert_eq!(response.status(), StatusCode::OK);
}
