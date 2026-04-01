use axum::extract::connect_info::ConnectInfo;
use axum::extract::ws::{Message, WebSocket, WebSocketUpgrade};
use axum::extract::{Path, State};
use axum::response::IntoResponse;
use portable_pty::{native_pty_system, CommandBuilder, PtySize};
use std::net::{IpAddr, SocketAddr};
use std::sync::Arc;
use subtle::ConstantTimeEq;
use tokio::sync::mpsc;
use tokio::time::{timeout, Duration};
use crate::error::AppError;
use crate::protocol::{Frame, SessionEventPayload};
use crate::state::AppState;
use crate::tmux::TmuxManager;

/// WebSocket handler that applies rate limiting by IP before upgrade,
/// then delegates to `handle_socket` for first-frame auth and PTY bridging.
pub async fn ws_handler(
    ws: WebSocketUpgrade,
    State(state): State<Arc<AppState>>,
    Path(session_id): Path<String>,
    ConnectInfo(addr): ConnectInfo<SocketAddr>,
) -> Result<impl IntoResponse, AppError> {
    if !state.rate_limiter.check(addr.ip()) {
        return Err(AppError::RateLimited);
    }
    if !TmuxManager::session_exists(&session_id).await {
        return Err(AppError::NotFound(format!("Session {} not found", session_id)));
    }
    Ok(ws.on_upgrade(move |socket| handle_socket(socket, state.clone(), session_id, addr.ip())))
}

async fn handle_socket(mut socket: WebSocket, state: Arc<AppState>, session_id: String, ip: IpAddr) {
    match timeout(Duration::from_secs(10), socket.recv()).await {
        Ok(Some(Ok(Message::Binary(data)))) => {
            match Frame::decode(&data) {
                Ok(Frame::Auth(token)) => {
                    let expected = state.config.auth.token.as_bytes();
                    let provided = token.as_slice();
                    if expected.len() != provided.len() || expected.ct_eq(provided).unwrap_u8() != 1 {
                        state.rate_limiter.record_failure(ip);
                        let frame = Frame::SessionEvent(SessionEventPayload {
                            event_type: "unauthorized".to_string(),
                            session_id: session_id.clone(),
                        });
                        let _ = socket.send(Message::Binary(frame.encode().into())).await;
                        let _ = socket.close().await;
                        return;
                    }
                    state.rate_limiter.reset(ip);
                }
                _ => {
                    let frame = Frame::SessionEvent(SessionEventPayload {
                        event_type: "unauthorized".to_string(),
                        session_id: session_id.clone(),
                    });
                    let _ = socket.send(Message::Binary(frame.encode().into())).await;
                    let _ = socket.close().await;
                    return;
                }
            }
        }
        _ => {
            let _ = socket.close().await;
            return;
        }
    }

    tracing::info!("WS connected for session: {}", session_id);
    let pty_system = native_pty_system();
    let size = PtySize {
        rows: 24,
        cols: 80,
        pixel_width: 0,
        pixel_height: 0,
    };

    let pair = match pty_system.openpty(size) {
        Ok(p) => { tracing::info!("PTY opened"); p }
        Err(e) => {
            tracing::error!("PTY open failed: {}", e);
            let frame = Frame::SessionEvent(SessionEventPayload {
                event_type: "error".to_string(),
                session_id: session_id.clone(),
            });
            let _ = socket.send(Message::Binary(frame.encode().into())).await;
            return;
        }
    };

    let mut cmd = CommandBuilder::new("tmux");
    cmd.arg("attach-session");
    cmd.arg("-t");
    cmd.arg(&session_id);

    let child = match pair.slave.spawn_command(cmd) {
        Ok(c) => { tracing::info!("tmux attach spawned"); c }
        Err(e) => {
            tracing::error!("tmux spawn failed: {}", e);
            let frame = Frame::SessionEvent(SessionEventPayload {
                event_type: "error".to_string(),
                session_id: session_id.clone(),
            });
            let _ = socket.send(Message::Binary(frame.encode().into())).await;
            return;
        }
    };
    drop(pair.slave);

    let reader = match pair.master.try_clone_reader() {
        Ok(r) => r,
        Err(_) => return,
    };
    let writer = match pair.master.take_writer() {
        Ok(w) => w,
        Err(_) => return,
    };
    let master = Arc::new(tokio::sync::Mutex::new(pair.master));

    let (pty_tx, mut pty_rx) = mpsc::channel::<Vec<u8>>(256);
    let (ws_tx, mut ws_rx) = mpsc::channel::<Vec<u8>>(256);

    let read_handle = tokio::task::spawn_blocking(move || {
        let mut reader = reader;
        let mut buf = [0u8; 4096];
        loop {
            match std::io::Read::read(&mut reader, &mut buf) {
                Ok(0) => { tracing::info!("PTY reader: EOF"); break; }
                Ok(n) => {
                    if pty_tx.blocking_send(buf[..n].to_vec()).is_err() {
                        tracing::info!("PTY reader: channel closed");
                        break;
                    }
                }
                Err(e) => { tracing::error!("PTY reader error: {}", e); break; }
            }
        }
    });

    let write_handle = tokio::task::spawn_blocking(move || {
        let mut writer = writer;
        while let Some(data) = ws_rx.blocking_recv() {
            if std::io::Write::write_all(&mut writer, &data).is_err() {
                break;
            }
        }
    });

    tracing::info!("Entering main WS loop");
    loop {
        tokio::select! {
            pty_msg = pty_rx.recv() => {
                match pty_msg {
                    Some(data) => {
                        let frame = Frame::Data(data);
                        if socket.send(Message::Binary(frame.encode().into())).await.is_err() {
                            tracing::info!("WS send failed, breaking");
                            break;
                        }
                    }
                    None => {
                        tracing::info!("PTY channel closed (process exited)");
                        break;
                    }
                }
            }
            msg = socket.recv() => {
                match msg {
                    Some(Ok(Message::Binary(data))) => {
                        match Frame::decode(&data) {
                            Ok(Frame::Data(input)) => {
                                if ws_tx.send(input).await.is_err() {
                                    tracing::info!("WS write channel closed");
                                    break;
                                }
                            }
                            Ok(Frame::Resize { cols, rows }) => {
                                let m = master.lock().await;
                                let _ = m.resize(PtySize {
                                    rows,
                                    cols,
                                    pixel_width: 0,
                                    pixel_height: 0,
                                });
                            }
                            Ok(Frame::Ping) => {
                                let pong = Frame::Pong;
                                let _ = socket.send(Message::Binary(pong.encode().into())).await;
                            }
                            _ => {}
                        }
                    }
                    Some(Ok(Message::Close(f))) => { tracing::info!("WS close frame: {:?}", f); break; }
                    None => { tracing::info!("WS recv None (disconnected)"); break; }
                    _ => {}
                }
            }
        }
    }

    drop(ws_tx);
    drop(master);
    read_handle.abort();
    let _ = read_handle.await;
    let _ = write_handle.await;
    drop(child);

    let event = Frame::SessionEvent(SessionEventPayload {
        event_type: "detached".to_string(),
        session_id,
    });
    let _ = socket.send(Message::Binary(event.encode().into())).await;
    let _ = socket.close().await;
}
