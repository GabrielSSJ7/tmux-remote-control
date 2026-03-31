---
phase: 2
slug: protocol-network-hardening
status: draft
nyquist_compliant: true
wave_0_complete: false
created: 2026-03-31
---

# Phase 2 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework (Rust)** | cargo test (built-in + tokio-test) |
| **Framework (Android)** | JUnit 4 + kotlinx-coroutines-test |
| **Quick run (Rust)** | `cd backend && cargo test 2>&1` |
| **Quick run (Android)** | `cd android && ./gradlew :app:testDebugUnitTest 2>&1` |
| **Full suite** | Both of the above |
| **Estimated runtime** | ~15 seconds (both) |

---

## Sampling Rate

- **After every task commit:** Run `cargo test -p remote-control-backend 2>&1`
- **After every plan wave:** Run both backend + Android test suites
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 15 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Task | Wave | Requirement | Test Type | Automated Command | Status |
|---------|------|------|------|-------------|-----------|-------------------|--------|
| 02-01-T1 | 02-01 | Frame::Auth variant | 1 | TOK-01 | unit | `cargo test protocol 2>&1` | pending |
| 02-01-T2 | 02-01 | ws_auth test stubs | 1 | TOK-02 | stub | `cargo test ws_auth -- --ignored 2>&1` | pending |
| 02-01-T3 | 02-01 | ws_handler rewrite | 1 | TOK-02, NET-02 | build+unit | `cargo build 2>&1 && cargo test 2>&1` | pending |
| 02-02-T1 | 02-02 | Frame.Auth (Android) | 1 | TOK-01 | unit | `./gradlew :app:testDebugUnitTest --tests "*.ProtocolTest"` | pending |
| 02-02-T2 | 02-02 | TerminalSocket rewrite + stubs | 1 | TOK-03 | unit+stub | `./gradlew :app:testDebugUnitTest 2>&1` | pending |
| 02-03-T1 | 02-03 | allowed_origins config | 1 | NET-01 | unit | `cargo test config 2>&1` | pending |
| 02-03-T2 | 02-03 | cors test stubs | 1 | NET-01 | stub | `cargo test cors -- --ignored 2>&1` | pending |
| 02-03-T3 | 02-03 | CorsLayer replacement | 1 | NET-01 | build+unit | `cargo build 2>&1 && cargo test 2>&1` | pending |

*Status: pending / green / red / flaky*

---

## Wave 0 Requirements

- [ ] `backend/src/protocol.rs` — add `auth_frame_roundtrip` test for 0x05 frame type (02-01 Task 1)
- [ ] `backend/tests/ws_auth_test.rs` — ignored stubs for WS auth handshake (02-01 Task 2)
- [ ] `backend/tests/cors_test.rs` — ignored stubs for CORS origin handling (02-03 Task 2)
- [ ] `android/app/src/test/.../ProtocolTest.kt` — extend with `authFrameRoundtrip` test (02-02 Task 1)
- [ ] `android/app/src/test/.../TerminalSocketTest.kt` — ignored stubs for Auth frame in onOpen (02-02 Task 2)

*All stub files are created by explicit plan tasks. Stubs use `#[ignore]`/`@Ignore` so the suite stays green.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Wireshark shows no token in WS URL | TOK-01 | Network traffic capture, not unit-testable | Capture WS upgrade, verify no `?token=` in URL |
| Android device connects with new protocol | TOK-03 | End-to-end device test | Build APK, connect to backend, verify terminal works |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references (ws_auth_test.rs, cors_test.rs, TerminalSocketTest.kt)
- [x] No watch-mode flags
- [x] Feedback latency < 15s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
