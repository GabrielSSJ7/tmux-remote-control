---
phase: 03-tls-android-security
plan: "01"
subsystem: backend
tags: [tls, rustls, security, config]
dependency_graph:
  requires: []
  provides: [tls-backend-support]
  affects: [backend/src/config.rs, backend/src/main.rs, SETUP.md]
tech_stack:
  added: [axum-server 0.7 tls-rustls]
  patterns: [conditional TLS binding, serde(default) backward compat]
key_files:
  created: []
  modified:
    - backend/Cargo.toml
    - backend/src/config.rs
    - backend/src/main.rs
    - backend/tests/common/mod.rs
    - SETUP.md
decisions:
  - "axum-server 0.7 (not 0.8) used to match existing axum 0.7 version"
  - "TlsConfig derives Default so existing struct initializers in tests only need tls: Default::default()"
  - "Empty string cert_path/key_path treated as absent via is_enabled() — no parse-time failure"
metrics:
  duration: "4 min"
  completed_date: "2026-04-01"
  tasks_completed: 3
  files_modified: 5
---

# Phase 3 Plan 1: TLS Backend Support Summary

**One-liner:** Conditional rustls TLS binding in Rust backend via axum-server, controlled entirely by `[tls]` section in config.toml with backward-compatible defaults.

## Tasks Completed

| Task | Description | Commit |
|------|-------------|--------|
| 1 | Add TlsConfig struct and axum-server dependency | 89d7bb0 |
| 2 | Implement conditional TLS binding in main.rs | b6aa007 |
| 3 | Update SETUP.md with mandatory TLS production section | 5019e72 |

## What Was Built

### TlsConfig struct (backend/src/config.rs)

Added `TlsConfig` with `cert_path: Option<String>`, `key_path: Option<String>`, and `is_enabled()` helper. The `Config` struct gained `#[serde(default)] pub tls: TlsConfig` for zero-migration backward compatibility — existing config.toml files without `[tls]` parse without error.

### Conditional TLS binding (backend/src/main.rs)

Replaced unconditional `TcpListener::bind` + `axum::serve` with a branch:
- `config.tls.is_enabled()` true → `axum_server::bind_rustls` with `RustlsConfig::from_pem_file`
- false → original `TcpListener` path with a warning log "plain HTTP - NOT for production"

### SETUP.md documentation

Replaced the optional TLS section with a mandatory-for-production section that includes:
- AVISO block explaining token interception risk
- Opcao A: native rustls with `openssl req` cert generation, full config.toml example, verification command, Android self-signed cert guidance
- Opcao B: Caddy reverse proxy as alternative (retained and reframed)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed Config struct initializer in integration test helper**
- **Found during:** Task 1 — cargo test failed because tests/common/mod.rs constructs Config directly
- **Issue:** Adding `tls` field to Config broke existing struct initializer in tests/common/mod.rs
- **Fix:** Added `tls: Default::default()` to the Config literal in tests/common/mod.rs
- **Files modified:** backend/tests/common/mod.rs
- **Commit:** 89d7bb0 (included in Task 1 commit)

**2. [Rule 3 - Dependency] Used axum-server 0.7 instead of 0.8**
- **Found during:** Task 1 — plan specified 0.8 but cargo resolved 0.7.3 (latest compatible with axum 0.7)
- **Fix:** Kept axum-server 0.7.3 as resolved by cargo — fully compatible with the existing axum 0.7 version
- **Files modified:** backend/Cargo.toml

## Self-Check: PASSED

- backend/src/config.rs — FOUND
- backend/src/main.rs — FOUND
- SETUP.md — FOUND
- 03-01-SUMMARY.md — FOUND
- commit 89d7bb0 — FOUND
- commit b6aa007 — FOUND
- commit 5019e72 — FOUND
