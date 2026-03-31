---
phase: 2
slug: protocol-network-hardening
status: draft
nyquist_compliant: false
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

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | Status |
|---------|------|------|-------------|-----------|-------------------|--------|
| 02-T1 | TBD | TBD | TOK-01 | unit | `cargo test protocol 2>&1` | pending |
| 02-T2 | TBD | TBD | TOK-01 | unit | `./gradlew :app:testDebugUnitTest --tests "*.ProtocolTest"` | pending |
| 02-T3 | TBD | TBD | TOK-02 | unit | `cargo test ws_auth 2>&1` | pending |
| 02-T4 | TBD | TBD | TOK-03 | unit | `./gradlew :app:testDebugUnitTest --tests "*.TerminalSocketTest"` | pending |
| 02-T5 | TBD | TBD | NET-01 | unit | `cargo test cors 2>&1` | pending |
| 02-T6 | TBD | TBD | NET-02 | unit | `cargo test rate_limit 2>&1` | pending |

*Status: pending / green / red / flaky*

---

## Wave 0 Requirements

- [ ] `backend/src/protocol.rs` — add `auth_frame_roundtrip` test for 0x05 frame type
- [ ] `backend/tests/ws_auth_test.rs` — stubs for reject non-Auth first frame, accept Auth frame
- [ ] `backend/tests/cors_test.rs` — stubs for known/unknown origin handling
- [ ] `android/app/src/test/.../ProtocolTest.kt` — extend with `authFrameRoundtrip` test
- [ ] `android/app/src/test/.../TerminalSocketTest.kt` — stub for Auth frame sent in onOpen

*Existing test framework covers infrastructure needs.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Wireshark shows no token in WS URL | TOK-01 | Network traffic capture, not unit-testable | Capture WS upgrade, verify no `?token=` in URL |
| Android device connects with new protocol | TOK-03 | End-to-end device test | Build APK, connect to backend, verify terminal works |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 15s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
