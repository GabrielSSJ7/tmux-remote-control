---
phase: 1
slug: quick-wins
status: draft
nyquist_compliant: true
wave_0_complete: false
created: 2026-03-31
---

# Phase 1 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Rust built-in test + tokio-test (`#[tokio::test]`) |
| **Config file** | `Cargo.toml` — no separate test config |
| **Quick run command** | `cargo test -p remote-control-backend 2>&1` |
| **Full suite command** | `cargo test -p remote-control-backend 2>&1` |
| **Estimated runtime** | ~5 seconds |

---

## Sampling Rate

- **After every task commit:** Run `cargo test -p remote-control-backend 2>&1`
- **After every plan wave:** Run `cargo test -p remote-control-backend 2>&1`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 5 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | Status |
|---------|------|------|-------------|-----------|-------------------|--------|
| 01-01-T1 | 01 | 1 | SEC-01, SEC-02 | git-state | `git check-ignore backend/config.toml && git ls-files --error-unmatch backend/config.toml 2>&1 \| grep "did not match"` | pending |
| 01-01-T2 | 01 | 1 | NET-03 | unit (TDD) | `cargo test default_bind_address` | pending |
| 01-02-T1 | 02 | 1 | LOG-02 | unit (TDD, RED) | `cargo test auth_config_debug_redacted` | pending |
| 01-02-T2 | 02 | 1 | LOG-01, LOG-02 | unit (GREEN) + structural | `cargo test auth_config_debug_redacted && cargo test load_config_writes_token_to_disk && grep 'REDACTED' backend/src/config.rs` | pending |

*Status: pending / green / red / flaky*

---

## Wave 0 Requirements

- [ ] `backend/src/config.rs` — add `default_bind_address` test stub for NET-03 (plan 01, Task 2 TDD)
- [ ] `backend/src/config.rs` — add `auth_config_debug_redacted` test stub for LOG-02 (plan 02, Task 1 TDD)
- [ ] `backend/src/config.rs` — add `load_config_writes_token_to_disk` test for LOG-01 file-write path (plan 02, Task 1)

*Existing test framework (cargo test) covers infrastructure needs.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| config.toml untracked by git | SEC-01 | Git index state, not code behavior | `git status backend/config.toml` must show untracked |
| *.env, *.key, *.pem blocked | SEC-02 | Git ignore rules, not code behavior | `touch test.env && git check-ignore test.env` must match |

---

## Structural Verifications (grep-based, no test runner)

| Behavior | Requirement | Why Structural | Automated Command |
|----------|-------------|----------------|-------------------|
| println uses [REDACTED] not raw token | LOG-01 | Stdout capture in Rust tests requires fd redirection; single-line edit verified by pattern match | `grep 'Generated new auth token: \[REDACTED\]' backend/src/config.rs` |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 5s
- [x] `nyquist_compliant: true` set in frontmatter
- [x] Per-task map references only plans 01 and 02 (the only existing plans)

**Approval:** ready
