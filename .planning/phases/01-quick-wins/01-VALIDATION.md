---
phase: 1
slug: quick-wins
status: draft
nyquist_compliant: false
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

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 1-01-01 | 01 | 0 | LOG-01 | unit | `cargo test load_config_log_redacted` | ❌ W0 | ⬜ pending |
| 1-01-02 | 01 | 0 | LOG-02 | unit | `cargo test auth_config_debug_redacted` | ❌ W0 | ⬜ pending |
| 1-01-03 | 01 | 0 | NET-03 | unit | `cargo test default_bind_address` | ❌ W0 | ⬜ pending |
| 1-02-01 | 02 | 1 | SEC-01 | manual | `git status backend/config.toml` | N/A | ⬜ pending |
| 1-02-02 | 02 | 1 | SEC-02 | manual | `git check-ignore test.env test.key` | N/A | ⬜ pending |
| 1-03-01 | 03 | 1 | LOG-01 | unit | `cargo test load_config_log_redacted` | ❌ W0 | ⬜ pending |
| 1-03-02 | 03 | 1 | LOG-02 | unit | `cargo test auth_config_debug_redacted` | ❌ W0 | ⬜ pending |
| 1-04-01 | 04 | 1 | NET-03 | unit | `cargo test default_bind_address` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `backend/src/config.rs` — add `load_config_log_redacted` test stub for LOG-01
- [ ] `backend/src/config.rs` — add `auth_config_debug_redacted` test stub for LOG-02
- [ ] `backend/src/config.rs` — add `default_bind_address` test stub for NET-03

*Existing test framework (cargo test) covers infrastructure needs.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| config.toml untracked by git | SEC-01 | Git index state, not code behavior | `git status backend/config.toml` must show untracked |
| *.env, *.key, *.pem blocked | SEC-02 | Git ignore rules, not code behavior | `touch test.env && git check-ignore test.env` must match |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 5s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
