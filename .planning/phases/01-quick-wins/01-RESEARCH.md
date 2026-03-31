# Phase 1: Quick Wins - Research

**Researched:** 2026-03-31
**Domain:** Git hygiene, Rust Debug trait suppression, token log redaction, Rust bind address defaults
**Confidence:** HIGH

## Summary

Phase 1 is pure configuration and minor code surgery — no new dependencies, no architectural changes. All five requirements (SEC-01, SEC-02, LOG-01, LOG-02, NET-03) have well-understood, battle-tested solutions in the Rust/git ecosystem. The riskiest item is `config.toml` already being in git history (committed in `106e2e8`), which means `.gitignore` alone cannot retroactively remove it — the planner must include a `git rm --cached` step. Everything else is a one-liner change.

The `println!` on line 43 of `config.rs` prints the raw token to stdout on first run. `AuthConfig` derives `Debug`, meaning `{:?}` on any `Config` or `AppState` parent will leak the token. Both are trivially fixed. The bind address `0.0.0.0` is hardcoded in `config.toml`; changing it to `127.0.0.1` requires only a one-line edit. No Cargo.toml changes are needed for any of these fixes.

**Primary recommendation:** Address requirements in this order — git exclusion (SEC-01/SEC-02) first so the fixed config.toml is never re-committed with a live token, then Debug removal (LOG-02) and println redaction (LOG-01), then bind address (NET-03). All five can be a single commit.

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| SEC-01 | config.toml with auth token excluded from git via root .gitignore | Root .gitignore does not exist yet; config.toml is already tracked — requires `git rm --cached` before .gitignore takes effect |
| SEC-02 | Root .gitignore protects `*.env`, `*.key`, `*.pem`, `*.cert` | Same .gitignore addition, straightforward glob patterns |
| LOG-01 | Auth token never printed to stdout — first-run message shows redacted value | Single `println!` in `config.rs:43` prints raw token; replace format string with `[REDACTED]` |
| LOG-02 | AuthConfig does not derive Debug | `AuthConfig` in `config.rs:17` derives `Debug`; remove it, implement manual `fmt::Debug` that prints `[REDACTED]` |
| NET-03 | Default bind address changed from `0.0.0.0` to `127.0.0.1` in config.toml | `backend/config.toml` line 2 has `host = "0.0.0.0"`; one-line change |
</phase_requirements>

## Current State Audit

This is critical context the planner must have:

| Item | Current State | Problem |
|------|--------------|---------|
| Root `.gitignore` | Does not exist | No protection against committing secrets |
| `backend/.gitignore` | Exists — excludes `/target`, `data.db`, `data.db-*` | Does NOT exclude `config.toml` |
| `backend/config.toml` | **Tracked in git** — present in commit `106e2e8` | Token is in git history |
| `AuthConfig` derive | `#[derive(Debug, Deserialize, Serialize, Clone)]` | Token exposed via `{:?}` |
| Token stdout print | `println!("Generated new auth token: {}", config.auth.token)` on `config.rs:43` | Raw token printed on first run |
| `config.server.host` | `"0.0.0.0"` in `config.toml` | Binds to all interfaces |

## Standard Stack

### Core
| Tool/Feature | Version | Purpose | Why Standard |
|---|---|---|---|
| `.gitignore` | git built-in | Prevent file tracking | Universal git mechanism |
| `git rm --cached` | git built-in | Untrack already-tracked file without deleting | Required when file is already in index |
| `std::fmt::Debug` manual impl | Rust stdlib | Custom debug output for sensitive types | Rust's official pattern for redacting fields |
| `config.toml` host field | toml 0.8 | Bind address configuration | Already wired — change value only |

### No New Dependencies Required

All five requirements are satisfied with existing tools:
- `serde`, `toml` already in Cargo.toml — no additions needed
- Rust stdlib `fmt::Debug` manual impl — no crates needed
- `.gitignore` — no tooling needed

## Architecture Patterns

### Pattern 1: Manual Debug impl for sensitive structs

**What:** Remove `Debug` from the derive macro and write a manual `fmt::Debug` implementation that redacts sensitive fields.

**When to use:** Any struct holding secrets that could appear in logs, panic output, or `{:?}` formatted strings.

**Example:**
```rust
use std::fmt;

#[derive(Deserialize, Serialize, Clone)]
pub struct AuthConfig {
    pub token: String,
}

impl fmt::Debug for AuthConfig {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.debug_struct("AuthConfig")
            .field("token", &"[REDACTED]")
            .finish()
    }
}
```

This pattern is idiomatic Rust. The `Config` and `ServerConfig` structs can keep `#[derive(Debug)]` unchanged — only `AuthConfig` needs the manual impl.

### Pattern 2: .gitignore with git rm --cached

**What:** Two-step process — first untrack the file from git's index, then add the pattern to `.gitignore`.

**When to use:** Any file that is already tracked by git that should be excluded going forward.

**Steps:**
```bash
git rm --cached backend/config.toml
```

Then root `.gitignore`:
```
backend/config.toml
*.env
*.key
*.pem
*.cert
```

**Critical note:** `git rm --cached` does NOT delete the file from disk — it only removes it from git's tracking. The file stays in place for the application to read.

**Also critical:** `config.toml` is in git history at `106e2e8`. The token `43f678...` is permanently in that commit. The `.gitignore` + `git rm --cached` fix prevents future leaks but does not rewrite history. The planner should note this as a known residual risk (history rewrite is out of scope for this phase).

### Pattern 3: Token redaction in startup log

**What:** Replace the raw token in `println!` with a static `[REDACTED]` string.

**Current code (`config.rs:43`):**
```rust
println!("Generated new auth token: {}", config.auth.token);
```

**Fixed code:**
```rust
println!("Generated new auth token: [REDACTED]");
```

Or, if the user needs to know where to find it:
```rust
println!("Generated new auth token — stored in config.toml");
```

The first form is simpler and satisfies LOG-01 exactly.

### Pattern 4: Default bind address

**What:** Change `host = "0.0.0.0"` to `host = "127.0.0.1"` in `backend/config.toml`.

**Effect:** On a fresh `cargo run`, `ss -tlnp` will show `127.0.0.1:48322` rather than `0.0.0.0:48322`. Users who need network exposure must explicitly change this — secure by default.

**No code changes required** — `main.rs` already reads `config.server.host` dynamically.

### Anti-Patterns to Avoid

- **Redacting in Serialize instead of Debug:** `Serialize` for `AuthConfig` must remain intact — the token must serialize correctly when `toml::to_string_pretty` writes it back to disk. Only `Debug` needs to be redacted.
- **Using a wrapper newtype for the token:** Overkill for this phase. Manual `Debug` impl on the existing struct is sufficient.
- **Rewriting git history in this phase:** `git filter-branch` or `git filter-repo` to purge the token from history is valid but is a separate, high-risk operation. This phase only stops future leaks.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Redacting debug output | Custom serializer, wrapper type | Manual `fmt::Debug` impl | Idiomatic Rust, zero deps, 6 lines |
| Preventing git commits | Pre-commit hooks | `.gitignore` | Simpler, no tooling required for this use case |
| Token masking in config write | Stripping token before serialize | Leave Serialize alone — only Debug is changed | Serialize must write the real token back to disk |

## Common Pitfalls

### Pitfall 1: .gitignore added without git rm --cached

**What goes wrong:** The file remains tracked. `git status` still shows it as modified. `.gitignore` only affects untracked files.

**Why it happens:** Developers forget that `.gitignore` has no effect on files already in the index.

**How to avoid:** Always run `git rm --cached <file>` before or after adding the pattern to `.gitignore`.

**Warning signs:** After adding to `.gitignore`, if `git status` still shows the file — the `git rm --cached` step was missed.

### Pitfall 2: Removing Debug from Config breaks test helpers

**What goes wrong:** `tests/common/mod.rs` constructs `AuthConfig { token: TEST_TOKEN.to_string() }` directly. This compiles fine. However, if any test uses `{:?}` on the full config, the output changes (token now shows `[REDACTED]`). This is correct behavior, not a bug.

**Why it happens:** Tests may assert on debug output (unlikely here, but worth checking).

**How to avoid:** Search for `{:?}` or `dbg!` usage involving `Config` or `AuthConfig` in tests before removing the derive.

**Verification:** `grep -r "AuthConfig\|Config" backend/tests/ --include="*.rs"` — currently no test asserts on debug output of AuthConfig.

### Pitfall 3: config.toml change is not the config.toml.example

**What goes wrong:** Changing the bind address in `config.toml` fixes the current default, but if a `config.toml.example` or template exists, it also needs updating.

**Why it doesn't apply here:** There is no `config.toml.example` in this project. Only `backend/config.toml` exists.

### Pitfall 4: Partial derive removal breaks Clone/Serialize

**What goes wrong:** Developer removes the entire `#[derive(Debug, ...)]` line instead of only removing `Debug` from it.

**How to avoid:** Edit the derive macro to keep `Deserialize, Serialize, Clone` and remove only `Debug`.

## Code Examples

### SEC-01 / SEC-02: Root .gitignore content
```
# Secrets
backend/config.toml
*.env
*.key
*.pem
*.cert
```

### LOG-02: AuthConfig with manual Debug
```rust
use std::fmt;
use serde::{Deserialize, Serialize};

#[derive(Deserialize, Serialize, Clone)]
pub struct AuthConfig {
    pub token: String,
}

impl fmt::Debug for AuthConfig {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.debug_struct("AuthConfig")
            .field("token", &"[REDACTED]")
            .finish()
    }
}
```

### LOG-01: Redacted startup message
```rust
if config.auth.token.is_empty() {
    config.auth.token = generate_token();
    let updated = toml::to_string_pretty(&config)?;
    std::fs::write(path, updated)?;
    println!("Generated new auth token: [REDACTED]");
}
```

### NET-03: config.toml after fix
```toml
[server]
host = "127.0.0.1"
port = 48322

[auth]
token = "43f678110501a9ed2b1a907c8ca8e69a25ed2161c43ff2784d6a9cd4ae4ecf8d"

[terminal]
scrollback_lines = 10000
default_shell = "/bin/bash"
```

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | Rust built-in test + tokio-test (via `#[tokio::test]`) |
| Config file | `Cargo.toml` — no separate test config file |
| Quick run command | `cargo test -p remote-control-backend 2>&1` |
| Full suite command | `cargo test -p remote-control-backend 2>&1` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| SEC-01 | config.toml untracked after fix | manual | `git status backend/config.toml` (exit 1 = untracked) | N/A — git check |
| SEC-02 | *.env, *.key, *.pem, *.cert blocked | manual | `touch /tmp/test.env && git -C . check-ignore /tmp/test.env` | N/A — git check |
| LOG-01 | println does not emit raw token | unit | `cargo test -p remote-control-backend load_config_log_redacted` | ❌ Wave 0 |
| LOG-02 | Debug output shows [REDACTED] not token | unit | `cargo test -p remote-control-backend auth_config_debug_redacted` | ❌ Wave 0 |
| NET-03 | Default host is 127.0.0.1 | unit | `cargo test -p remote-control-backend default_bind_address` | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** `cargo test -p remote-control-backend 2>&1`
- **Per wave merge:** `cargo test -p remote-control-backend 2>&1`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `backend/src/config.rs` — add `load_config_log_redacted` test: capture stdout, assert token not present
- [ ] `backend/src/config.rs` — add `auth_config_debug_redacted` test: assert `format!("{:?}", auth_config)` contains `[REDACTED]` and not the token value
- [ ] `backend/src/config.rs` — add `default_bind_address` test: parse a config and assert `server.host == "127.0.0.1"`

Note: SEC-01 and SEC-02 are git state checks — not unit-testable in Rust. They are verified manually as success criteria.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|---|---|---|---|
| Committing config.toml with token | git-ignored, untracked | This phase | Token no longer enters future commits |
| `#[derive(Debug)]` on AuthConfig | Manual `fmt::Debug` impl | This phase | Token cannot appear in `{:?}` output |
| `println!` raw token on first run | Print `[REDACTED]` | This phase | stdout safe to observe in CI/logs |
| `host = "0.0.0.0"` | `host = "127.0.0.1"` | This phase | No unintended network exposure on fresh start |

## Open Questions

1. **Token already in git history**
   - What we know: Commit `106e2e8` contains `backend/config.toml` with the full token value
   - What's unclear: Whether the user wants history scrubbing (requires `git filter-repo`, force push, all collaborators re-clone)
   - Recommendation: Document as known residual risk in this phase; treat history rewrite as a separate out-of-scope action. The token in that commit is a non-rotating test token — if the same token is used in production, it should be rotated after this phase completes.

2. **config.toml.example / template file**
   - What we know: No template exists currently
   - What's unclear: Whether the planner should create a `config.toml.example` committed to git (with `token = ""`) so new users know the file format
   - Recommendation: Create `backend/config.toml.example` with `token = ""` as part of this phase — provides onboarding value with zero security risk. This is additive and does not block any requirement.

## Sources

### Primary (HIGH confidence)
- Rust stdlib `std::fmt::Debug` — manual impl via `f.debug_struct()` is documented in std docs and is the canonical pattern for redacting fields
- git documentation — `git rm --cached` behavior is well-documented and stable
- Direct source inspection of `/home/gluz/code/remote-control/backend/src/config.rs` — `println!` on line 43 confirmed
- Direct source inspection of `backend/config.toml` — `host = "0.0.0.0"` confirmed on line 2
- `git ls-files` — confirmed `backend/config.toml` is tracked

### Secondary (MEDIUM confidence)
- Rust 1.94.0 confirmed via `rustc --version` — all patterns used are stable since Rust 1.0

### Tertiary (LOW confidence)
- None — all claims verified against source files or stable language features

## Metadata

**Confidence breakdown:**
- SEC-01/SEC-02 (git hygiene): HIGH — verified current state, standard git mechanics
- LOG-01 (println redaction): HIGH — exact line located in source
- LOG-02 (Debug removal): HIGH — derive confirmed in source, manual impl pattern is idiomatic stable Rust
- NET-03 (bind address): HIGH — exact config line confirmed, main.rs reads it dynamically

**Research date:** 2026-03-31
**Valid until:** 2026-05-31 (stable domain — git, Rust stdlib, toml config; no fast-moving dependencies)
