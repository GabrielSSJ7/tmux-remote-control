# Roadmap: Remote Control — Security Hardening

## Overview

This milestone eliminates 9 security vulnerabilities discovered during a comprehensive audit of the Remote Control project. Work proceeds from lowest-risk quick wins (git hygiene, log redaction, bind address) through protocol-breaking changes (WebSocket auth migration, CORS, rate limiting) to the highest-effort hardening layer (TLS support, Android encrypted storage, UI masking). Each phase delivers independently verifiable security improvements without breaking existing terminal functionality.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [x] **Phase 1: Quick Wins** - Git hygiene, log redaction, and default bind address — zero breaking changes (completed 2026-03-31)
- [ ] **Phase 2: Protocol & Network Hardening** - WebSocket auth migration to first frame, CORS lockdown, WS rate limiting
- [ ] **Phase 3: TLS & Android Security** - rustls TLS support, encrypted token storage, UI masking on Android

## Phase Details

### Phase 1: Quick Wins
**Goal**: The most exploitable passive leaks are eliminated — config never enters git, token never appears in logs, and the backend stops advertising itself on all interfaces by default
**Depends on**: Nothing (first phase)
**Requirements**: SEC-01, SEC-02, LOG-01, LOG-02, NET-03
**Success Criteria** (what must be TRUE):
  1. Running `git status` after touching config.toml shows it as untracked — it cannot be staged or committed
  2. `*.env`, `*.key`, `*.pem`, and `*.cert` files are blocked from git commit by .gitignore rules
  3. Starting the backend and checking stdout shows the token as `[REDACTED]`, never the actual value
  4. A panic or debug print involving AuthConfig does not expose the token value
  5. A fresh `cargo run` binds to `127.0.0.1` by default — `ss -tlnp` shows no `0.0.0.0` entry for the server port
**Plans:** 2/2 plans complete
Plans:
- [ ] 01-01-PLAN.md — Git hygiene (.gitignore, untrack config.toml) and secure default bind address
- [ ] 01-02-PLAN.md — Token log redaction (manual Debug impl, redacted println)

### Phase 2: Protocol & Network Hardening
**Goal**: The auth token cannot be intercepted from WebSocket URLs or logs, legitimate origins are the only ones the server accepts, and WebSocket endpoints are protected from connection floods
**Depends on**: Phase 1
**Requirements**: TOK-01, TOK-02, TOK-03, NET-01, NET-02
**Success Criteria** (what must be TRUE):
  1. Capturing WebSocket upgrade traffic in Wireshark shows no token in the URL query string
  2. A WebSocket client that sends a non-auth first frame immediately receives a rejection and the connection closes
  3. An Android device connects, authenticates, and runs terminal commands successfully using the new first-frame protocol
  4. A curl request with an `Origin` header not in the allowlist receives a CORS rejection (403 or CORS error)
  5. Sending 100+ WebSocket connection attempts from one IP within a minute triggers rate limiting before the auth frame is ever read
**Plans:** 3 plans
Plans:
- [ ] 02-01-PLAN.md — Backend protocol migration (Frame::Auth, first-frame handshake, WS rate limiting)
- [ ] 02-02-PLAN.md — Android protocol migration (Frame.Auth, token-free URLs, first-frame auth in onOpen)
- [ ] 02-03-PLAN.md — CORS lockdown (explicit origin allowlist from config.toml)

### Phase 3: TLS & Android Security
**Goal**: Traffic between the Android app and backend is encrypted end-to-end, the token is never stored in plaintext on the device, and the app prevents screen capture of sensitive values
**Depends on**: Phase 2
**Requirements**: TLS-01, TLS-02, AND-01, AND-02, AND-03
**Success Criteria** (what must be TRUE):
  1. The backend starts with TLS when cert/key paths are set in config.toml — `openssl s_client` connects successfully
  2. SETUP.md contains explicit TLS configuration instructions covering cert generation, config.toml fields, and a warning that plaintext is not acceptable in production
  3. Uninstalling and reinstalling the Android app — or accessing the device with root — does not reveal the token in plaintext via `adb shell` file inspection
  4. The token field in SettingsScreen displays dots (masked) by default, not the raw token characters
  5. Android's recent-apps thumbnail and screenshots show a blank/black screen for the app — FLAG_SECURE is active
**Plans:** 3 plans
Plans:
- [ ] 03-01-PLAN.md — Backend TLS via rustls (axum-server conditional bind) and SETUP.md update
- [ ] 03-02-PLAN.md — Android encrypted token storage (Keystore AES-256-GCM + DataStore)
- [ ] 03-03-PLAN.md — Android UI hardening (token masking, FLAG_SECURE, cleartext lockdown)

## Progress

**Execution Order:**
Phases execute in numeric order: 1 → 2 → 3

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Quick Wins | 2/2 | Complete   | 2026-03-31 |
| 2. Protocol & Network Hardening | 0/3 | Planned | - |
| 3. TLS & Android Security | 0/3 | Planned | - |
