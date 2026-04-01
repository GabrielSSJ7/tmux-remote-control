---
phase: 02-protocol-network-hardening
plan: 03
subsystem: api
tags: [rust, axum, tower-http, cors, config, security]

requires:
  - phase: 01-quick-wins
    provides: ServerConfig struct in config.rs with host/port

provides:
  - allowed_origins: Vec<String> field on ServerConfig with serde default
  - CorsLayer using explicit AllowOrigin::list from config instead of permissive()
  - create_router(allowed_origins: &[String]) signature
  - CORS integration test stubs in cors_test.rs

affects:
  - 02-04 and any future plans that call create_router()
  - android client if CORS origins need to be listed in config.toml

tech-stack:
  added: []
  patterns:
    - "Origin allowlist: CORS configured from config.toml allowed_origins field, not hardcoded or permissive"
    - "Serde default: new optional config fields use #[serde(default)] for backward compat"
    - "Test stubs with #[ignore]: unimplemented integration tests use #[ignore] + todo!() to document future work without blocking CI"

key-files:
  created:
    - backend/tests/cors_test.rs
  modified:
    - backend/src/config.rs
    - backend/src/lib.rs
    - backend/src/main.rs
    - backend/tests/common/mod.rs
    - backend/tests/health_test.rs

key-decisions:
  - "CORS allowlist from config: create_router() accepts &[String] instead of reading state, because CORS layer is built before AppState is attached"
  - "Empty allowed_origins logs tracing::warn and rejects all cross-origin — not a startup error, just restrictive behavior"
  - "Backward compat via serde default: existing config.toml without allowed_origins parses to empty vec, no migration needed"

patterns-established:
  - "Pattern: create_router signature takes explicit config parameters rather than reading global state"
  - "Pattern: New optional config fields use #[serde(default)] to maintain backward compatibility"

requirements-completed: [NET-01]

duration: 4min
completed: 2026-04-01
---

# Phase 02 Plan 03: Explicit CORS Origin Allowlist Summary

**Replaced CorsLayer::permissive() with AllowOrigin::list from config.toml, adding allowed_origins: Vec<String> to ServerConfig with serde default for backward compatibility**

## Performance

- **Duration:** 4 min
- **Started:** 2026-04-01T12:06:46Z
- **Completed:** 2026-04-01T12:10:46Z
- **Tasks:** 3
- **Files modified:** 5

## Accomplishments

- `ServerConfig` has `allowed_origins: Vec<String>` with `#[serde(default)]` — existing config.toml files without this field parse to empty vec
- `create_router` now accepts `&[String]` and builds `CorsLayer::new().allow_origin(AllowOrigin::list(...))` — `CorsLayer::permissive()` is gone
- Empty `allowed_origins` logs `tracing::warn` and rejects all cross-origin requests
- CORS integration test stubs in `cors_test.rs` (4 ignored tests) document expected behavior for future implementation

## Task Commits

Each task was committed atomically:

1. **Task 1: Add allowed_origins to ServerConfig with serde default** - `d7d3cc2` (feat)
2. **Task 2: Create CORS integration test stubs** - `652d7fb` (test)
3. **Task 3: Replace CorsLayer::permissive() with explicit origin allowlist** - `a96ee13` (feat)

**Plan metadata:** (docs commit follows)

## Files Created/Modified

- `backend/src/config.rs` - Added `allowed_origins: Vec<String>` with `#[serde(default)]` to `ServerConfig`; added `parses_config_without_allowed_origins` test
- `backend/src/lib.rs` - `create_router` now accepts `&[String]`, builds explicit `CorsLayer` with `AllowOrigin::list`
- `backend/src/main.rs` - Passes `&config.server.allowed_origins` to `create_router`
- `backend/tests/common/mod.rs` - `test_state()` includes `allowed_origins: vec!["http://localhost"]`
- `backend/tests/health_test.rs` - Updated to pass `allowed_origins` from state to `create_router`
- `backend/tests/cors_test.rs` - 4 ignored integration test stubs for CORS behavior

## Decisions Made

- `create_router()` takes `&[String]` rather than reading from AppState because the CORS layer must be built before `with_state()` is called — passing the slice explicitly is cleaner than a two-phase init
- Empty `allowed_origins` is a warning-level event, not an error — the server starts and rejects cross-origin requests, which is secure behavior
- `#[serde(default)]` on `allowed_origins` means no migration or config file change is required for existing deployments

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Pre-existing Frame::Auth tests failed to compile**
- **Found during:** Task 1 (verifying config tests)
- **Issue:** `protocol.rs` had `Frame::Auth` tests referencing a variant that didn't exist in the enum, causing compile failure that blocked `cargo test`
- **Fix:** Added `Auth(Vec<u8>)` variant to `Frame` enum with `encode`/`decode` support at `TYPE_AUTH = 0x05` (the constant and tests already existed)
- **Files modified:** `backend/src/protocol.rs`
- **Verification:** All protocol tests pass including the 3 new auth frame tests
- **Note:** File was already updated by a prior session's formatter before my edit was needed

---

**Total deviations:** 1 auto-fixed (1 blocking pre-existing compile error)
**Impact on plan:** Necessary to unblock `cargo test`. No scope creep — the constant, type byte, and test bodies were already written; only the enum variant was missing.

## Issues Encountered

- System tool reminders showed files as "reverted" after `cargo build`, but git diff confirmed changes were on disk — the reminders were comparing to the tool's cached read state, not current disk state. Confirmed actual content with `git diff` before re-applying.

## User Setup Required

None - no external service configuration required. Users who want CORS enabled should add `allowed_origins` to their `config.toml`:

```toml
[server]
host = "127.0.0.1"
port = 48322
allowed_origins = ["http://localhost:3000"]
```

Without this field, the server starts successfully (serde default = empty vec) and rejects all cross-origin requests.

## Next Phase Readiness

- CORS is now locked to an explicit allowlist — ready for TLS setup in phase 02-04
- `create_router` signature change is a breaking change for any callers — all callers (main.rs, health_test.rs) updated
- CORS integration test stubs in `cors_test.rs` are documented future work, not blocking

## Self-Check: PASSED

- FOUND: backend/src/config.rs
- FOUND: backend/src/lib.rs
- FOUND: backend/src/main.rs
- FOUND: backend/tests/cors_test.rs
- FOUND: .planning/phases/02-protocol-network-hardening/02-03-SUMMARY.md
- FOUND commit: d7d3cc2 (Task 1)
- FOUND commit: 652d7fb (Task 2)
- FOUND commit: a96ee13 (Task 3)
- VERIFIED: CorsLayer::permissive() not found in any .rs file

---
*Phase: 02-protocol-network-hardening*
*Completed: 2026-04-01*
