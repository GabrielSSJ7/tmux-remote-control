---
phase: 3
slug: tls-android-security
status: active
nyquist_compliant: true
wave_0_complete: true
created: 2026-03-31
---

# Phase 3 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework (Rust)** | cargo test (built-in + tempfile) |
| **Framework (Android)** | JUnit 4 + MockK |
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
| 03-01-T1 | 03-01 | 1 | TLS-01 | unit (TDD) | `cd backend && cargo test config::tests 2>&1` | pending |
| 03-01-T2 | 03-01 | 1 | TLS-01 | build | `cd backend && cargo build 2>&1` | pending |
| 03-01-T3 | 03-01 | 1 | TLS-02 | structural | `grep -q "[tls]" SETUP.md && grep -q "cert_path" SETUP.md && echo PASS` | pending |
| 03-02-T1 | 03-02 | 1 | AND-01 | unit (TDD) | `cd android && ./gradlew :app:testDebugUnitTest --tests "com.remotecontrol.data.settings.TokenEncryptorTest" 2>&1` | pending |
| 03-02-T2 | 03-02 | 1 | AND-01 | unit | `cd android && ./gradlew :app:testDebugUnitTest 2>&1` | pending |
| 03-03-T1 | 03-03 | 1 | AND-02, AND-03 | compile + structural | `cd android && ./gradlew :app:compileDebugKotlin 2>&1 && grep -q networkSecurityConfig app/src/main/AndroidManifest.xml` | pending |
| 03-03-T2 | 03-03 | 1 | AND-02, AND-03 | human-verify | User confirms on device: masked token, blank screenshots, blank recent-apps | pending |

*Status: pending / green / red / flaky*

---

## Wave 0 Requirements

- [x] `backend/src/config.rs` — TlsConfig tests are defined in 03-01-T1 behavior block (TDD task, tests written first)
- [x] `android/app/src/test/.../TokenEncryptorTest.kt` — encrypt/decrypt roundtrip tests defined in 03-02-T1 behavior block (TDD task, tests written first)

*Both Wave 0 obligations are met by TDD tasks that create tests before implementation. No separate Wave 0 task needed.*

*Note: Android Keystore requires device/emulator. Unit tests mock the encryption layer via TestTokenCrypto.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| TLS connection works end-to-end | TLS-01 | Requires real cert/key and running server | `openssl s_client -connect localhost:48322` |
| Token not in plaintext on device | AND-01 | Requires rooted device/emulator inspection | `adb shell` file inspection in app data dir |
| Token field shows dots | AND-02 | Visual UI check | Open Settings screen, verify dots |
| Recent-apps shows blank screen | AND-03 | OS-level behavior | Open recent apps, verify blank thumbnail |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 15s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved
