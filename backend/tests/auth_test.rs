mod common;

use axum::body::Body;
use axum::http::{Request, StatusCode};
use axum::routing::get;
use axum::Router;
use tower::ServiceExt;
use remote_control_backend::auth::AuthToken;

async fn protected(_: AuthToken) -> &'static str {
    "secret"
}

async fn make_app() -> Router {
    let state = common::test_state().await;
    Router::new()
        .route("/protected", get(protected))
        .with_state(state)
}

#[tokio::test]
async fn valid_token_passes() {
    let app = make_app().await;
    let response = app
        .oneshot(
            Request::builder()
                .uri("/protected")
                .header("authorization", format!("Bearer {}", common::TEST_TOKEN))
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(response.status(), StatusCode::OK);
}

#[tokio::test]
async fn missing_token_returns_401() {
    let app = make_app().await;
    let response = app
        .oneshot(Request::builder().uri("/protected").body(Body::empty()).unwrap())
        .await
        .unwrap();
    assert_eq!(response.status(), StatusCode::UNAUTHORIZED);
}

#[tokio::test]
async fn wrong_token_returns_401() {
    let app = make_app().await;
    let response = app
        .oneshot(
            Request::builder()
                .uri("/protected")
                .header("authorization", "Bearer wrong-token")
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(response.status(), StatusCode::UNAUTHORIZED);
}
