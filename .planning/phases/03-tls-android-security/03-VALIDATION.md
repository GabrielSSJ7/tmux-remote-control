---
phase: 3
slug: tls-android-security
status: draft
nyquist_compliant: false
wave_0_complete: false
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
| 03-T1 | TBD | TBD | TLS-01 | unit | `cargo test config 2>&1` | pending |
| 03-T2 | TBD | TBD | TLS-01 | build | `cargo build 2>&1` | pending |
| 03-T3 | TBD | TBD | TLS-02 | structural | `grep -q "TLS" SETUP.md` | pending |
| 03-T4 | TBD | TBD | AND-01 | unit | `./gradlew :app:testDebugUnitTest 2>&1` | pending |
| 03-T5 | TBD | TBD | AND-02 | structural | `grep -q "PasswordVisualTransformation" android/app/src/main/kotlin/com/remotecontrol/ui/settings/SettingsScreen.kt` | pending |
| 03-T6 | TBD | TBD | AND-03 | structural | `grep -q "FLAG_SECURE" android/app/src/main/kotlin/com/remotecontrol/MainActivity.kt` | pending |

*Status: pending / green / red / flaky*

---

## Wave 0 Requirements

- [ ] `backend/src/config.rs` — add TlsConfig struct tests (parses with/without TLS section)
- [ ] `android/app/src/test/.../TokenEncryptorTest.kt` — encrypt/decrypt roundtrip test

*Note: Android Keystore requires device/emulator. Unit tests mock the encryption layer.*

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

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 15s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
