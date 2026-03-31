# Requirements: Remote Control — Security Hardening

**Defined:** 2026-03-31
**Core Value:** Auth token must never be exposed through git, logs, URLs, or cleartext traffic

## v1 Requirements

### Secrets Protection

- [ ] **SEC-01**: config.toml with auth token is excluded from git tracking via root .gitignore
- [ ] **SEC-02**: Root .gitignore also protects *.env, *.key, *.pem, *.cert files from accidental commit

### Token Transmission

- [ ] **TOK-01**: WebSocket authentication uses first binary frame instead of URL query parameter
- [ ] **TOK-02**: Backend WebSocket handler validates auth frame before accepting terminal data
- [ ] **TOK-03**: Android TerminalSocket sends auth token as first frame after connection

### Token Logging

- [ ] **LOG-01**: Auth token is never printed to stdout — first-run message shows redacted value
- [ ] **LOG-02**: AuthConfig does not derive Debug — prevents token in panic backtraces and debug logs

### Transport Security

- [ ] **TLS-01**: Backend supports TLS via rustls with configurable cert/key paths in config.toml
- [ ] **TLS-02**: SETUP.md documents TLS as mandatory for production with clear instructions

### Android Token Security

- [ ] **AND-01**: Auth token stored using EncryptedSharedPreferences instead of plaintext DataStore
- [ ] **AND-02**: Token field in SettingsScreen uses PasswordVisualTransformation (masked by default)
- [ ] **AND-03**: MainActivity sets FLAG_SECURE to prevent screenshots and screen recording

### Network Hardening

- [ ] **NET-01**: CorsLayer configured with explicit allowed origins instead of permissive()
- [ ] **NET-02**: WebSocket endpoint applies rate limiting before token validation
- [ ] **NET-03**: Default bind address changed from 0.0.0.0 to 127.0.0.1 in config.toml

## v2 Requirements

### Advanced Auth

- **ADV-01**: Token rotation endpoint for changing token without config file edit
- **ADV-02**: Audit logging for all authentication attempts (success/failure)

### Network

- **ADV-03**: Automatic TLS certificate management (Let's Encrypt integration)
- **ADV-04**: Rate limiter LRU eviction for stale IP entries

## Out of Scope

| Feature | Reason |
|---------|--------|
| Multi-user / per-user tokens | Personal use tool — single token sufficient |
| Client certificate pinning | Overkill for home network use case |
| OAuth / social login | Not applicable for CLI terminal tool |
| Session ID crypto hardening | Low risk — token is primary security gate |
| WAF / DDoS protection | Home network, not internet-facing by default |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| SEC-01 | Phase 1 | Pending |
| SEC-02 | Phase 1 | Pending |
| LOG-01 | Phase 1 | Pending |
| LOG-02 | Phase 1 | Pending |
| NET-03 | Phase 1 | Pending |
| TOK-01 | Phase 2 | Pending |
| TOK-02 | Phase 2 | Pending |
| TOK-03 | Phase 2 | Pending |
| NET-01 | Phase 2 | Pending |
| NET-02 | Phase 2 | Pending |
| TLS-01 | Phase 3 | Pending |
| TLS-02 | Phase 3 | Pending |
| AND-01 | Phase 3 | Pending |
| AND-02 | Phase 3 | Pending |
| AND-03 | Phase 3 | Pending |

**Coverage:**
- v1 requirements: 15 total
- Mapped to phases: 15
- Unmapped: 0

---
*Requirements defined: 2026-03-31*
*Last updated: 2026-03-31 after initial definition*
