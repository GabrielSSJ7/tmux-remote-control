mod common;

use axum::body::Body;
use axum::http::{Request, StatusCode};
use axum::Router;
use http_body_util::BodyExt;
use tower::ServiceExt;
use remote_control_backend::routes;

async fn make_app() -> Router {
    let state = common::test_state().await;
    routes::api_routes().with_state(state)
}

fn auth_header() -> (&'static str, String) {
    ("authorization", format!("Bearer {}", common::TEST_TOKEN))
}

#[tokio::test]
async fn create_and_list_commands() {
    let app = make_app().await;
    let (key, val) = auth_header();

    let response = app.clone()
        .oneshot(
            Request::builder()
                .method("POST")
                .uri("/commands")
                .header(key, &val)
                .header("content-type", "application/json")
                .body(Body::from(r#"{"name":"Deploy","command":"git push origin main","category":"git"}"#))
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(response.status(), StatusCode::CREATED);
    let body = response.into_body().collect().await.unwrap().to_bytes();
    let created: serde_json::Value = serde_json::from_slice(&body).unwrap();
    assert_eq!(created["name"], "Deploy");

    let response = app
        .oneshot(
            Request::builder()
                .uri("/commands")
                .header(key, &val)
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(response.status(), StatusCode::OK);
    let body = response.into_body().collect().await.unwrap().to_bytes();
    let list: Vec<serde_json::Value> = serde_json::from_slice(&body).unwrap();
    assert_eq!(list.len(), 1);
    assert_eq!(list[0]["name"], "Deploy");
}

#[tokio::test]
async fn update_command() {
    let app = make_app().await;
    let (key, val) = auth_header();

    let response = app.clone()
        .oneshot(
            Request::builder()
                .method("POST")
                .uri("/commands")
                .header(key, &val)
                .header("content-type", "application/json")
                .body(Body::from(r#"{"name":"Old","command":"echo old","category":"test"}"#))
                .unwrap(),
        )
        .await
        .unwrap();
    let body = response.into_body().collect().await.unwrap().to_bytes();
    let created: serde_json::Value = serde_json::from_slice(&body).unwrap();
    let id = created["id"].as_str().unwrap();

    let response = app
        .oneshot(
            Request::builder()
                .method("PUT")
                .uri(&format!("/commands/{}", id))
                .header(key, &val)
                .header("content-type", "application/json")
                .body(Body::from(r#"{"name":"New"}"#))
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(response.status(), StatusCode::OK);
    let body = response.into_body().collect().await.unwrap().to_bytes();
    let updated: serde_json::Value = serde_json::from_slice(&body).unwrap();
    assert_eq!(updated["name"], "New");
    assert_eq!(updated["command"], "echo old");
}

#[tokio::test]
async fn delete_command() {
    let app = make_app().await;
    let (key, val) = auth_header();

    let response = app.clone()
        .oneshot(
            Request::builder()
                .method("POST")
                .uri("/commands")
                .header(key, &val)
                .header("content-type", "application/json")
                .body(Body::from(r#"{"name":"Tmp","command":"echo tmp","category":"test"}"#))
                .unwrap(),
        )
        .await
        .unwrap();
    let body = response.into_body().collect().await.unwrap().to_bytes();
    let created: serde_json::Value = serde_json::from_slice(&body).unwrap();
    let id = created["id"].as_str().unwrap();

    let response = app.clone()
        .oneshot(
            Request::builder()
                .method("DELETE")
                .uri(&format!("/commands/{}", id))
                .header(key, &val)
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(response.status(), StatusCode::NO_CONTENT);

    let response = app
        .oneshot(
            Request::builder()
                .uri("/commands")
                .header(key, &val)
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();
    let body = response.into_body().collect().await.unwrap().to_bytes();
    let list: Vec<serde_json::Value> = serde_json::from_slice(&body).unwrap();
    assert_eq!(list.len(), 0);
}

#[tokio::test]
async fn delete_nonexistent_returns_404() {
    let app = make_app().await;
    let (key, val) = auth_header();

    let response = app
        .oneshot(
            Request::builder()
                .method("DELETE")
                .uri("/commands/nonexistent")
                .header(key, &val)
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(response.status(), StatusCode::NOT_FOUND);
}
