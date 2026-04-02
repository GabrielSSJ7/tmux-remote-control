use std::net::SocketAddr;
use std::sync::Arc;
use tokio::net::TcpListener;
use tracing_subscriber::EnvFilter;
use axum_server::tls_rustls::RustlsConfig;
use remote_control_backend::{config, db, rate_limit::RateLimiter, state::AppState, create_router};

#[tokio::main]
async fn main() {
    tracing_subscriber::fmt()
        .with_env_filter(EnvFilter::from_default_env().add_directive("info".parse().unwrap()))
        .json()
        .init();

    let config = config::load_config("config.toml").expect("Failed to load config");
    let db = db::init_pool("sqlite:data.db?mode=rwc")
        .await
        .expect("Failed to init database");
    let rate_limiter = RateLimiter::new(60, 60, 3600);

    let state = Arc::new(AppState {
        db,
        config: config.clone(),
        rate_limiter,
    });

    let app = create_router(&config.server.allowed_origins).with_state(state);
    let addr = format!("{}:{}", config.server.host, config.server.port);

    if config.tls.is_enabled() {
        let cert = config.tls.cert_path.as_ref().unwrap();
        let key = config.tls.key_path.as_ref().unwrap();
        let tls_config = RustlsConfig::from_pem_file(cert, key)
            .await
            .expect("Failed to load TLS certificates");
        tracing::info!("Listening on {} (TLS)", addr);
        axum_server::bind_rustls(addr.parse().unwrap(), tls_config)
            .serve(app.into_make_service_with_connect_info::<SocketAddr>())
            .await
            .unwrap();
    } else {
        let listener = TcpListener::bind(&addr).await.unwrap();
        tracing::info!("Listening on {} (plain HTTP - NOT for production)", addr);
        axum::serve(listener, app.into_make_service_with_connect_info::<SocketAddr>())
            .await
            .unwrap();
    }
}
