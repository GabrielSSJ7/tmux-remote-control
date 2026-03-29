use tokio::net::TcpListener;

#[tokio::main]
async fn main() {
    let app = remote_control_backend::create_router();
    let listener = TcpListener::bind("0.0.0.0:48322").await.unwrap();
    println!("Listening on 0.0.0.0:48322");
    axum::serve(listener, app).await.unwrap();
}
