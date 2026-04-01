mod common;

#[tokio::test]
#[ignore]
async fn cors_allows_listed_origin() {
    todo!("Send request with Origin header in allowed_origins, assert Access-Control-Allow-Origin in response")
}

#[tokio::test]
#[ignore]
async fn cors_rejects_unlisted_origin() {
    todo!("Send request with Origin header NOT in allowed_origins, assert no Access-Control-Allow-Origin in response")
}

#[tokio::test]
#[ignore]
async fn cors_preflight_returns_allow_headers() {
    todo!("Send OPTIONS preflight for allowed origin, assert 200 with correct CORS headers")
}

#[tokio::test]
#[ignore]
async fn cors_empty_origins_rejects_all() {
    todo!("Configure empty allowed_origins, send cross-origin request, assert rejection")
}
