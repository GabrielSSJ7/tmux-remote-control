use axum::extract::ws::{Message, WebSocket, WebSocketUpgrade};
use axum::extract::{Path, Query, State};
use axum::response::IntoResponse;
use portable_pty::{native_pty_system, CommandBuilder, PtySize};
use std::sync::Arc;
use tokio::sync::mpsc;
use crate::error::AppError;
use crate::protocol::{Frame, SessionEventPayload};
use crate::state::AppState;
use crate::tmux::TmuxManager;

#[derive(serde::Deserialize)]
pub struct WsQuery {
    token: String,
}

/// WebSocket handler that bridges a client connection to a tmux session via a PTY.
pub async fn ws_handler(
    ws: WebSocketUpgrade,
    State(state): State<Arc<AppState>>,
    Path(session_id): Path<String>,
    Query(query): Query<WsQuery>,
) -> Result<impl IntoResponse, AppError> {
    if query.token != state.config.auth.token {
        return Err(AppError::Unauthorized);
    }
    if !TmuxManager::session_exists(&session_id).await {
        return Err(AppError::NotFound(format!("Session {} not found", session_id)));
    }
    Ok(ws.on_upgrade(move |socket| handle_socket(socket, session_id)))
}

async fn handle_socket(mut socket: WebSocket, session_id: String) {
    let pty_system = native_pty_system();
    let size = PtySize {
        rows: 24,
        cols: 80,
        pixel_width: 0,
        pixel_height: 0,
    };

    let pair = match pty_system.openpty(size) {
        Ok(p) => p,
        Err(_) => {
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

    let _child = match pair.slave.spawn_command(cmd) {
        Ok(c) => c,
        Err(_) => {
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
                Ok(0) => break,
                Ok(n) => {
                    if pty_tx.blocking_send(buf[..n].to_vec()).is_err() {
                        break;
                    }
                }
                Err(_) => break,
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

    loop {
        tokio::select! {
            Some(data) = pty_rx.recv() => {
                let frame = Frame::Data(data);
                if socket.send(Message::Binary(frame.encode().into())).await.is_err() {
                    break;
                }
            }
            msg = socket.recv() => {
                match msg {
                    Some(Ok(Message::Binary(data))) => {
                        match Frame::decode(&data) {
                            Ok(Frame::Data(input)) => {
                                if ws_tx.send(input).await.is_err() {
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
                    Some(Ok(Message::Close(_))) | None => break,
                    _ => {}
                }
            }
        }
    }

    drop(ws_tx);
    read_handle.abort();
    let _ = write_handle.await;

    let event = Frame::SessionEvent(SessionEventPayload {
        event_type: "detached".to_string(),
        session_id,
    });
    let _ = socket.send(Message::Binary(event.encode().into())).await;
    let _ = socket.close().await;
}
