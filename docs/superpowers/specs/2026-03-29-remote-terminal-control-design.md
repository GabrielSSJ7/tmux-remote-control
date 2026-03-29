# Remote Terminal Control -- Design Spec

## Overview

App Android nativo (Kotlin) que se conecta a um backend Rust rodando no PC pessoal do usuário, permitindo visualizar e interagir com sessões de terminal remotamente. Usa tmux como backend de persistência, permitindo iniciar sessões no PC e continuar pelo celular (e vice-versa).

## Architecture

```
[App Android] <--WebSocket/TLS--> [Backend Rust no PC] <--PTY--> [tmux session] <--> [shell/claude/etc]
```

### Components

**Backend (Rust, roda no PC)**
- Serviço que escuta numa porta, gerencia sessões de terminal via tmux
- Expõe REST API + WebSocket
- Framework: Axum (async, WebSocket nativo)
- Interação com tmux via `tokio::process::Command`
- PTY via `portable-pty` (cross-platform, API ergonômica)
- Persistência: SQLite via `sqlx` (async, compile-time checked queries)
- TLS: `rustls` ou reverse proxy (Caddy/nginx)

**App Android (Kotlin nativo)**
- Jetpack Compose UI
- OkHttp (WebSocket) + Retrofit (REST)
- Terminal emulator: lib `terminal-emulator` do Termux (open source)

## Session Management

- Cada sessão = uma sessão tmux com nome padronizado (`rc-{uuid}`)
- Sessões criadas via app (REST `POST /sessions`) ou detectadas automaticamente (`tmux list-sessions`)
- Quando o app desconecta, a sessão continua viva no tmux
- Ao reconectar, o backend faz re-attach e envia o buffer atual
- Múltiplos clientes podem ver a mesma sessão simultaneamente
- Fluxo PC-para-celular: inicia sessão no PC, sai de casa, abre o app, seleciona a sessão e continua

## REST API

| Method | Route | Description |
|--------|-------|-------------|
| GET | `/sessions` | Lista todas as sessões tmux (ativas e detached) |
| POST | `/sessions` | Cria nova sessão tmux |
| DELETE | `/sessions/{id}` | Mata uma sessão |
| GET | `/sessions/{id}/status` | Status da sessão (running, idle, output recente) |
| GET | `/commands` | Lista biblioteca de comandos |
| POST | `/commands` | Cadastra novo comando |
| PUT | `/commands/{id}` | Edita comando |
| DELETE | `/commands/{id}` | Remove comando |
| POST | `/sessions/{id}/exec` | Envia comando da biblioteca direto pra sessão |

Authentication: header `Authorization: Bearer <token>` em todas as requests.

Response errors: `{ "error": "code", "message": "...", "request_id": "..." }`

## WebSocket Protocol

Route: `ws /sessions/{id}/terminal`

Authentication: token no handshake (`?token=...` ou primeiro frame).

Binary frame protocol:

| Byte 0 (type) | Payload |
|---|---|
| `0x00` | **Data** -- raw terminal bytes (output from server, input from client) |
| `0x01` | **Resize** -- `{cols, rows}` JSON when screen rotates or font changes |
| `0x02` | **Ping/Pong** -- heartbeat every 15s |
| `0x03` | **Session event** -- notifications (session created, ended, etc) |

## Security

### Transport: TLS
- All traffic encrypted (REST and WebSocket)
- Let's Encrypt + domain (recommended) or self-signed cert pinned in app

### Authentication: Token + PIN
- Backend generates 256-bit token on first setup
- Token registered in app via QR code (local pairing, one-time)
- Optional 4-6 digit PIN to open the app

### Brute-force Protection
- Rate limiting: max 5 failed attempts per minute
- After 10 consecutive failures, block IP for 1 hour
- All connection attempts logged

### Network
- Port forwarding on a high port (e.g., 48322)
- Firewall accepts connections only on that port
- Optional future: Tailscale as alternative to port forwarding

### Scope
- Backend runs under user's Linux account (not root)
- tmux sessions inherit user permissions

## App Android -- Screens

### 1. Sessions (home)
- List of active sessions with status (running/idle)
- Visual indicator for sessions with new output
- Button to create new session
- Swipe to kill session

### 2. Terminal
- Full-screen terminal with real rendering (ANSI colors, cursor, scroll)
- Extra key bar (Tab, Ctrl, Esc, arrows)
- Floating button to open command library
- Pinch-to-zoom for font size
- Auto-reconnect with visual indicator

### 3. Command Library
- Bottom sheet overlay on terminal
- Search and categories (e.g., "git", "claude", "docker", "custom")
- Each command: name, short description, command string
- Tap = send to active session
- Long press = edit
- Add new command button
- Import/export commands (JSON)

### 4. Settings
- Server address (IP:port)
- QR code pairing (token)
- App access PIN
- Terminal preferences (font, size, light/dark theme)
- Scroll buffer size

## Error Handling and Resilience

### Backend
- Detects tmux crash/session death (PID poll), notifies app via WebSocket frame `0x03`
- On restart, rediscovers existing tmux sessions automatically
- Structured JSON logs (stdout) with configurable level

### App
- Disconnect: visual banner + auto reconnect with exponential backoff (1s, 2s, 4s, 8s... max 30s)
- On reconnect: receives current tmux buffer, re-renders without context loss
- REST errors: toast/snackbar with readable message
- Server unreachable: "no connection" screen with manual retry
- Background: WebSocket closes, reconnects on resume with buffer sync

### Network
- REST timeout: 10s
- WebSocket keepalive: ping every 15s
- 3 consecutive ping failures = disconnected, triggers reconnect

## Persistence (SQLite)

### Tables

**commands**
- id (TEXT, PK)
- name (TEXT)
- command (TEXT)
- description (TEXT, nullable)
- category (TEXT)
- icon (TEXT, nullable)
- created_at (TEXT)
- updated_at (TEXT)

**config**
- key (TEXT, PK)
- value (TEXT)

## Testing

### Backend (Rust)
- **Unit:** parsing de sessões tmux, protocolo WebSocket (frames), CRUD de comandos, autenticação/rate limiting
- **Integration:** API REST completa, fluxo WebSocket (connect, send input, receive output), reconexão e re-attach
- **Tooling:** `cargo test` + `tokio::test`

### App Android
- **Unit:** ViewModels, lógica de reconexão, parsing de respostas, gerenciamento da biblioteca
- **UI:** fluxo de pareamento, navegação entre sessões, biblioteca de comandos, comportamento de desconexão/reconexão
- **Tooling:** JUnit + Compose Testing + MockWebServer

### Coverage
- 80% overall minimum
- 100% critical paths: authentication, WebSocket protocol, reconnection
