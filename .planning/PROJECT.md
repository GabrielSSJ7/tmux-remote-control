# Remote Control — Security Hardening

## What This Is

Security hardening milestone for the Remote Control project — a Rust backend + Android app for remote terminal control via WebSocket and tmux. This milestone addresses 9 security vulnerabilities discovered during a comprehensive audit, ranging from CRITICAL (token exposure, cleartext traffic) to MEDIUM (bind address, rate limiting gaps).

## Core Value

The auth token must never be exposed through git history, logs, URLs, or cleartext traffic — a single token compromise grants full shell access to the host machine.

## Requirements

### Validated

- ✓ Remote terminal sessions via WebSocket + tmux — existing
- ✓ ANSI terminal emulator with 256-color and 24-bit RGB — existing
- ✓ Command library CRUD with categories — existing
- ✓ Token-based authentication with constant-time comparison — existing
- ✓ REST API rate limiting per IP — existing
- ✓ Auto-reconnection with exponential backoff — existing
- ✓ Settings persistence via DataStore — existing

### Active

- [ ] Root .gitignore protecting config.toml, *.env, *.key, *.pem from version control
- [ ] WebSocket auth moved from URL query parameter to first binary frame
- [ ] Auth token never logged — redacted println, no Debug derive on AuthConfig
- [ ] TLS support via rustls or mandatory reverse proxy documentation
- [ ] EncryptedSharedPreferences for token storage on Android
- [ ] PasswordVisualTransformation on token field + FLAG_SECURE on Activity
- [ ] CORS restricted to specific origins instead of permissive
- [ ] Rate limiting applied to WebSocket endpoint
- [ ] Default bind address changed from 0.0.0.0 to 127.0.0.1

### Out of Scope

- Multi-user support / per-user tokens — personal use tool, single token is sufficient
- Client certificate pinning — overkill for home network use case
- OAuth / social login — not applicable for CLI terminal tool
- Session ID cryptographic hardening — low risk given token is the primary gate
- Graceful shutdown / session recovery — operational concern, not security

## Context

- Backend: Rust (Axum 0.7, Tokio, SQLite, portable-pty)
- Android: Kotlin (Jetpack Compose, Retrofit, OkHttp WebSocket)
- Custom binary WebSocket protocol (5 frame types: Data, Resize, Ping, Pong, SessionEvent)
- Codebase map exists at `.planning/codebase/`
- Security audit completed 2026-03-31 identifying all 9 vulnerabilities
- Project is for personal use on home network, but security posture must assume network exposure

## Constraints

- **Backward compatibility**: WebSocket protocol changes require updating both backend and Android app simultaneously
- **Single binary**: Backend must remain a single Rust binary, no container orchestration
- **Android min SDK**: API 26 (Android 8.0+) — EncryptedSharedPreferences requires API 23+, compatible
- **No breaking changes**: Existing terminal functionality must continue working after all security fixes

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Move WS auth to first frame (not header) | WebSocket upgrade headers are not consistently supported by all Android HTTP clients; first-frame auth is simpler and equally secure | — Pending |
| Use rustls (not native-tls) | Pure Rust, no OpenSSL dependency, simpler cross-compilation | — Pending |
| Default bind 127.0.0.1 | Secure by default; users opt-in to network exposure | — Pending |

---
*Last updated: 2026-03-31 after initialization*
