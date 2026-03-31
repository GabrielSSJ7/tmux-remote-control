# Phase 3: TLS & Android Security - Research

**Researched:** 2026-03-31
**Domain:** Rust TLS (rustls/axum-server), Android Keystore encryption, Android UI security
**Confidence:** HIGH

## Summary

This phase adds three independent security layers: TLS on the backend (TLS-01, TLS-02), encrypted token storage on Android (AND-01), and screen-capture prevention (AND-02, AND-03). Each requirement maps cleanly to a well-understood pattern in its respective ecosystem with no significant design ambiguity.

For the backend, `axum-server` with the `tls-rustls` feature is the correct integration path — it wraps `axum_server::bind_rustls()` around the existing router with near-zero code churn. TLS is activated when cert/key paths are present in `config.toml`; the server falls back to plain HTTP when they are absent. The current `main.rs` is a single `TcpListener` + `axum::serve` call, so the change is a conditional branch on config, not a rewrite.

For Android, `EncryptedSharedPreferences` was deprecated in June 2025 (security-crypto 1.1.0-beta01). The correct replacement is direct Android Keystore + AES-256-GCM encryption stored in the existing `DataStore`. This requires no new dependencies — only `javax.crypto.*` and `java.security.*` (platform APIs). `SettingsStore` already uses `DataStore`; the migration is wrapping the token read/write in encrypt/decrypt calls. `PasswordVisualTransformation` for AND-02 and `FLAG_SECURE` for AND-03 are both one-liner changes.

**Primary recommendation:** Three plans — one for TLS backend + SETUP.md, one for Android encrypted storage, one for Android UI hardening (masking + FLAG_SECURE). All can execute in parallel since they touch disjoint files.

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| TLS-01 | Backend supports TLS via rustls with configurable cert/key paths in config.toml | axum-server 0.8 `bind_rustls()` + `RustlsConfig::from_pem_file()` |
| TLS-02 | SETUP.md documents TLS as mandatory for production with clear instructions | Existing SETUP.md Section 3 needs rewrite as mandatory, not optional |
| AND-01 | Auth token stored using EncryptedSharedPreferences instead of plaintext DataStore | Android Keystore AES-256-GCM wrapping existing DataStore token field |
| AND-02 | Token field in SettingsScreen uses PasswordVisualTransformation (masked by default) | Single `visualTransformation` parameter on existing `OutlinedTextField` |
| AND-03 | MainActivity sets FLAG_SECURE to prevent screenshots and screen recording | Two-line `window.setFlags(...)` call in `onCreate` before `setContent` |
</phase_requirements>

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| axum-server | 0.8 | TLS-aware server binding for axum | Official integration path; axum itself has no built-in TLS |
| rustls | 0.23 (transitive via axum-server) | Pure-Rust TLS implementation | Project decision: no OpenSSL dependency |
| tokio-rustls | 0.26 (transitive via axum-server) | Async TLS stream over Tokio | Required by axum-server internals |
| Android Keystore | Platform API (API 18+) | Secure key storage | Built-in; keys never leave secure hardware |
| javax.crypto.* | Platform API | AES-256-GCM encrypt/decrypt | No external library needed |
| PasswordVisualTransformation | Compose UI (already in BOM) | Mask token field | Part of existing compose dependency |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| rustls-pki-types | 1.9 (transitive) | PEM file parsing types | Required by axum-server tls-rustls |
| openssl (CLI tool) | system | Generate self-signed cert for testing | Only needed by operator, not in Cargo.toml |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| axum-server | tokio-rustls directly (low-level) | Low-level requires manual hyper integration — more boilerplate, no benefit for this use case |
| axum-server | native-tls | Banned by project decision — requires OpenSSL |
| Android Keystore direct | EncryptedSharedPreferences | Deprecated June 2025 — do not use |
| Android Keystore direct | Tink library | External dependency; Tink is heavyweight for a single token; native Keystore is sufficient |

**Installation (backend):**
```bash
# Add to backend/Cargo.toml [dependencies]:
axum-server = { version = "0.8", features = ["tls-rustls"] }
```

**Android — no new dependencies needed.** All APIs are from platform (`android.security.keystore`, `javax.crypto`, `android.security.keystore.KeyGenParameterSpec`).

## Architecture Patterns

### Recommended Project Structure

No new directories needed. Changes are localized:

```
backend/src/
├── config.rs        # Add TlsConfig struct with optional cert_path/key_path
└── main.rs          # Conditional bind: bind_rustls() or TcpListener + axum::serve

android/app/src/main/kotlin/com/remotecontrol/
├── data/settings/
│   └── SettingsStore.kt    # Wrap token read/write with encrypt/decrypt
│   └── TokenEncryptor.kt   # New: AES-256-GCM using Android Keystore
└── ui/settings/
│   └── SettingsScreen.kt   # Add visualTransformation to token field
└── MainActivity.kt         # Add window.setFlags(FLAG_SECURE)

android/app/src/main/res/xml/
└── network_security_config.xml  # Disable cleartext (cleartextTrafficPermitted="false")
```

### Pattern 1: Conditional TLS in main.rs

**What:** Branch on whether `config.tls.cert_path` and `config.tls.key_path` are set. When set, use `axum_server::bind_rustls()`; when absent, fall back to current `TcpListener` + `axum::serve` path.

**When to use:** Any server that must support both dev (no cert) and production (TLS) without code changes.

**Example:**
```rust
// Source: https://github.com/tokio-rs/axum/blob/main/examples/tls-rustls/src/main.rs
use axum_server::tls_rustls::RustlsConfig;

match (config.tls.cert_path.as_ref(), config.tls.key_path.as_ref()) {
    (Some(cert), Some(key)) => {
        let tls_config = RustlsConfig::from_pem_file(cert, key).await?;
        axum_server::bind_rustls(addr.parse()?, tls_config)
            .serve(app.into_make_service_with_connect_info::<SocketAddr>())
            .await?;
    }
    _ => {
        let listener = TcpListener::bind(&addr).await?;
        axum::serve(listener, app.into_make_service_with_connect_info::<SocketAddr>())
            .await?;
    }
}
```

### Pattern 2: TlsConfig in config.rs

**What:** Add optional `[tls]` section to `Config`. Both fields optional — no TLS section in config means plain HTTP.

```rust
// New struct added to config.rs
#[derive(Debug, Deserialize, Serialize, Clone, Default)]
pub struct TlsConfig {
    pub cert_path: Option<String>,
    pub key_path: Option<String>,
}

// Updated Config struct
pub struct Config {
    pub server: ServerConfig,
    pub auth: AuthConfig,
    pub terminal: TerminalConfig,
    pub tls: TlsConfig,  // new — defaults to empty via #[serde(default)]
}
```

**TOML representation (when TLS enabled):**
```toml
[tls]
cert_path = "/path/to/cert.pem"
key_path = "/path/to/key.pem"
```

### Pattern 3: Android Keystore AES-256-GCM Token Encryptor

**What:** Dedicated class that wraps Android Keystore to generate/retrieve an AES-256-GCM key, then encrypt/decrypt token strings. IV is prepended to ciphertext and stored together in DataStore (Base64 encoded).

**When to use:** Storing any sensitive string in DataStore without external crypto libraries.

```kotlin
// Source: https://developer.android.com/privacy-and-security/cryptography
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.util.Base64

object TokenEncryptor {
    private const val KEY_ALIAS = "remote_control_token_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_SIZE = 12
    private const val TAG_LENGTH = 128

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        keyGen.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()
        )
        return keyGen.generateKey()
    }

    fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val combined = cipher.iv + ciphertext
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decrypt(encoded: String): String {
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, IV_SIZE)
        val ciphertext = combined.copyOfRange(IV_SIZE, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }
}
```

### Pattern 4: FLAG_SECURE in MainActivity

**What:** Set FLAG_SECURE before `setContent` in `onCreate`. Effect is immediate and persistent — all app windows (including recent-apps thumbnail) show blank.

```kotlin
// Source: https://developer.android.com/security/fraud-prevention/activities
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    window.setFlags(
        WindowManager.LayoutParams.FLAG_SECURE,
        WindowManager.LayoutParams.FLAG_SECURE
    )
    // ... existing setContent block
}
```

### Pattern 5: PasswordVisualTransformation in SettingsScreen

**What:** Add `visualTransformation = PasswordVisualTransformation()` to the existing token `OutlinedTextField`. Optionally add a trailing icon to toggle visibility (common UX).

```kotlin
// Source: Jetpack Compose docs
OutlinedTextField(
    value = tokenField,
    onValueChange = { tokenField = it; vm.setToken(it) },
    label = { Text("Auth Token") },
    visualTransformation = PasswordVisualTransformation(),
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    modifier = Modifier.fillMaxWidth(),
    singleLine = true
)
```

### Pattern 6: network_security_config.xml — disable cleartext

**What:** Update existing config to deny cleartext traffic globally. This is compatible with the current `cleartextTrafficPermitted="true"` being the only entry — replace with `false`.

```xml
<!-- Source: https://developer.android.com/privacy-and-security/security-config -->
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>
</network-security-config>
```

**Warning:** Setting `cleartextTrafficPermitted="false"` will break plain WebSocket connections (`ws://`). This is the desired end state, but it means the app will only work once TLS is configured on the backend. For development, users may need to revert or use a `<debug-overrides>` block.

### Anti-Patterns to Avoid

- **Storing IV separately from ciphertext:** IV and ciphertext must travel together. Prepend IV to ciphertext, store as one Base64 blob.
- **Hardcoding the key alias string in multiple places:** Define it once as a constant in `TokenEncryptor`.
- **Calling `getOrCreateKey()` on every encrypt/decrypt:** Cache the `SecretKey` reference within the object lifetime, or rely on the Keystore lookup being fast.
- **Putting `[tls]` section as required in TOML:** Must be optional (`#[serde(default)]`) so existing `config.toml` files without a `[tls]` section continue to work.
- **Initializing `RustlsConfig` outside the TLS branch:** `from_pem_file` is async and fails if the files don't exist; only call it when both paths are configured.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| TLS handshake/cipher suite | Custom TLS state machine | axum-server + rustls | Cipher suites, handshake state machine, certificate parsing — all have exploitable edge cases |
| Secure key generation | Random bytes in app storage | Android Keystore | Keys in Keystore cannot be extracted by other apps, even with root access to `/data` |
| PEM parsing for Rust TLS | Manual string parsing | `RustlsConfig::from_pem_file()` | PEM format has encoding edge cases (trailing newlines, header variants) |
| AES IV management | Counter or fixed IV | Cipher.iv from random init | Fixed IV destroys GCM security guarantee — every encryption must generate a fresh random IV |

**Key insight:** Both the Rust TLS layer and Android encryption layer have API surfaces designed to eliminate the common implementation mistakes (fixed IVs, wrong cipher modes, key derivation weaknesses). Use the provided APIs exactly as documented.

## Common Pitfalls

### Pitfall 1: axum-server version incompatibility
**What goes wrong:** Using `axum-server = "0.7"` with `axum = "0.7"` compiles, but `axum-server = "0.8"` requires no version change to axum 0.7 since axum-server 0.8 still targets axum 0.7 compatibility.
**Why it happens:** axum-server is a third-party crate not owned by the axum team; versions are independent.
**How to avoid:** Check axum-server changelog. As of 2026-03-31, axum-server 0.8 works with axum 0.7 (which is what the project uses). Do not upgrade axum itself.
**Warning signs:** Trait bound errors mentioning `Service` or `MakeService` at compile time.

### Pitfall 2: `tls-rustls` feature requires aws-lc-rs or ring
**What goes wrong:** `axum-server` with `tls-rustls` feature pulls in `rustls` which needs a crypto backend — by default `aws-lc-rs`. On some Linux builds this requires cmake and C toolchain.
**Why it happens:** rustls 0.23 changed default crypto provider to aws-lc-rs. The feature `tls-rustls-no-provider` skips this and allows manually registering `ring`.
**How to avoid:** Try `axum-server = { version = "0.8", features = ["tls-rustls"] }` first. If build fails due to aws-lc-rs, switch to `features = ["tls-rustls-no-provider"]` and add `ring` or `aws-lc-rs` manually.
**Warning signs:** Build errors mentioning `cmake`, `aws-lc-sys`, or `ring` during `cargo build`.

### Pitfall 3: network_security_config.xml blocks development
**What goes wrong:** Setting `cleartextTrafficPermitted="false"` in base-config breaks `ws://` connections immediately, before TLS is set up on the backend. App stops working in dev.
**Why it happens:** The config applies to all traffic, including WebSocket upgrades. OkHttp respects NSC at the system level.
**How to avoid:** Add a `<debug-overrides>` block that re-enables cleartext for debug builds, or plan to apply `cleartextTrafficPermitted="false"` as the final step once TLS is confirmed working end-to-end.
**Warning signs:** `CLEARTEXT communication to X not permitted` in Logcat after changing NSC.

### Pitfall 4: EncryptedSharedPreferences migration
**What goes wrong:** The existing `SettingsStore` stores token in plain DataStore. After migration to encrypted DataStore, existing users who already have a token stored will lose it (encrypted read of plaintext value will fail to decrypt).
**Why it happens:** The encrypted token in DataStore is a Base64-encoded IV+ciphertext blob. Reading a previously stored plaintext token value through the decrypt path throws a BadPaddingException.
**How to avoid:** On first read, check if the stored value is already a valid Base64 blob of expected length (IV_SIZE + min ciphertext). If not, treat it as a first run (empty) and require the user to re-enter the token. Do not attempt to migrate — just clear it.
**Warning signs:** `javax.crypto.BadPaddingException` or `AEADBadTagException` on decrypt call.

### Pitfall 5: GCM tag verification failure after app reinstall
**What goes wrong:** After uninstalling and reinstalling the app, the Android Keystore key is deleted. DataStore persistence may survive if backed up. Attempting to decrypt old ciphertext with a newly generated key will throw `AEADBadTagException`.
**Why it happens:** Android Keystore keys are tied to the app installation. Auto-backup (if enabled) may back up DataStore files but not Keystore keys.
**How to avoid:** Catch `AEADBadTagException` in the decrypt path, delete the stored encrypted token from DataStore, and prompt the user to re-enter their token. This is the correct behavior — the encrypted token is irretrievable and the user must reconfigure.
**Warning signs:** `AEADBadTagException` during decrypt, typically after reinstall or device restore.

### Pitfall 6: Self-signed cert and OkHttp rejection
**What goes wrong:** When the backend uses a self-signed certificate, Android OkHttp (used by the app) rejects the connection with `SSLHandshakeException: Trust anchor for certification path not found`.
**Why it happens:** The system trust store only contains public CAs. Self-signed certs are not in it.
**How to avoid:** Two options: (1) Use a domain-validated certificate via Let's Encrypt/Caddy (recommended for public use) — no Android changes needed. (2) For self-signed: add the cert to `res/raw/` and reference it in `network_security_config.xml` under `<domain-config>`. Document this procedure in SETUP.md.
**Warning signs:** `SSLHandshakeException` in Logcat when connecting to `wss://`.

## Code Examples

### Self-signed cert generation (for testing)
```bash
openssl req -x509 -newkey rsa:4096 \
  -keyout key.pem -out cert.pem \
  -sha256 -days 365 -nodes \
  -subj "/CN=remote-control"
```

### Verify TLS is working
```bash
openssl s_client -connect localhost:48322 -showcerts
# Expected: certificate chain printed, handshake complete
```

### Verify token not in plaintext on device
```bash
adb shell run-as com.remotecontrol
find /data/data/com.remotecontrol -name "*.preferences_pb" -exec cat {} \;
# Expected: binary/encrypted content, no plaintext token visible
```

### config.toml with TLS enabled
```toml
[server]
host = "127.0.0.1"
port = 48322

[auth]
token = ""

[tls]
cert_path = "/home/user/certs/cert.pem"
key_path = "/home/user/certs/key.pem"

[terminal]
scrollback_lines = 10000
default_shell = "/bin/bash"
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| EncryptedSharedPreferences | Android Keystore + DataStore directly | June 2025 (security-crypto 1.1.0-beta01) | Must use direct Keystore API; no wrapper library |
| native-tls / OpenSSL for Rust TLS | rustls (pure Rust) | ~2022, mainstream 2024 | No C dependency; simpler cross-compilation |
| MasterKeys API (security-crypto) | KeyGenParameterSpec directly | Same deprecation wave | Use platform API directly |

**Deprecated/outdated:**
- `EncryptedSharedPreferences`: deprecated, do not use in new code
- `MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)`: deprecated alongside EncryptedSharedPreferences
- `native-tls` in Rust: functional but requires OpenSSL; project decision rules it out

## Open Questions

1. **aws-lc-rs build requirement on the project's Arch Linux system**
   - What we know: `axum-server` 0.8 with `tls-rustls` feature defaults to `aws-lc-rs` as crypto backend for rustls 0.23, which may need cmake
   - What's unclear: Whether the current Arch Linux build environment already has cmake/clang installed
   - Recommendation: Try `features = ["tls-rustls"]` first; if build fails, fall back to `features = ["tls-rustls-no-provider"]` + explicitly add `ring` crate

2. **SETUP.md write permission (CLAUDE.md rule)**
   - What we know: CLAUDE.md states "MD files ONLY if user explicitly requests"; STATE.md notes "Phase 3 TLS-02 requires SETUP.md to be written (user must explicitly request MD files per CLAUDE.md — confirm before writing)"
   - What's unclear: Whether spawning this research from `/gsd:plan-phase` counts as implicit approval for TLS-02
   - Recommendation: The planner should flag this as requiring explicit user confirmation before writing SETUP.md. TLS-01 can proceed independently.

3. **OkHttp TLS enforcement scope**
   - What we know: The app connects via `ws://` using OkHttp. Setting `cleartextTrafficPermitted="false"` will block this.
   - What's unclear: Whether the plan should enforce this in Phase 3 (breaking change requiring TLS to be live) or defer to after TLS is confirmed working
   - Recommendation: Apply `cleartextTrafficPermitted="false"` as the last step of Phase 3, gated on TLS-01 being verified. Include `<debug-overrides>` re-enabling cleartext for debug builds.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | Rust: built-in `cargo test` + `tempfile` (already in dev-deps) |
| Config file | none (uses `#[cfg(test)]` inline) |
| Quick run command | `cd backend && cargo test` |
| Full suite command | `cd backend && cargo test -- --test-threads=1` |

Android tests: JUnit 4 + MockK (already in `build.gradle.kts`)

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| TLS-01 | `Config` with `[tls]` section deserializes to `TlsConfig { cert_path: Some(...), key_path: Some(...) }` | unit | `cd backend && cargo test config::tests` | ❌ Wave 0 |
| TLS-01 | `Config` without `[tls]` section deserializes with `tls = TlsConfig::default()` | unit | `cd backend && cargo test config::tests` | ❌ Wave 0 |
| TLS-02 | SETUP.md contains the string "TLS" and cert generation command | manual | inspect SETUP.md | N/A |
| AND-01 | `TokenEncryptor.encrypt()` returns non-empty string != input | unit (Android) | `./gradlew test` | ❌ Wave 0 |
| AND-01 | `TokenEncryptor.decrypt(encrypt(x)) == x` roundtrip | unit (Android) | `./gradlew test` | ❌ Wave 0 |
| AND-02 | Token field has `visualTransformation` set to `PasswordVisualTransformation` | manual / UI test | visual inspection | N/A |
| AND-03 | FLAG_SECURE set in MainActivity.onCreate | unit (check window flags) | manual / inspect | manual-only |

**TLS-01 live verification (success criterion):** `openssl s_client -connect localhost:48322` — manual, cannot be automated in unit tests without spinning up a real TLS listener.

**AND-03 flag verification:** FLAG_SECURE effect (blank thumbnail) is not automatable via unit test — verify manually by opening recent apps.

### Sampling Rate
- **Per task commit:** `cd backend && cargo test`
- **Per wave merge:** `cd backend && cargo test && cd android && ./gradlew test`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `backend/src/config.rs` — add `TlsConfig` struct tests (parses with TLS section, parses without TLS section)
- [ ] `android/app/src/test/kotlin/com/remotecontrol/TokenEncryptorTest.kt` — encrypt/decrypt roundtrip test (requires Robolectric or instrumented test for Keystore)

**Note on Android Keystore testing:** `KeyStore.getInstance("AndroidKeyStore")` requires an Android device or emulator. Unit tests using Robolectric do not support the AndroidKeyStore provider. The roundtrip test must be an instrumented test (`androidTest`), not a local JVM test.

## Sources

### Primary (HIGH confidence)
- `docs.rs/axum-server/latest` — bind_rustls API, RustlsConfig, tls-rustls feature
- `github.com/tokio-rs/axum/examples/tls-rustls` — official axum TLS example (Cargo.toml + main.rs)
- `developer.android.com/privacy-and-security/cryptography` — Android Keystore AES-256-GCM pattern (official docs)
- `developer.android.com/security/fraud-prevention/activities` — FLAG_SECURE official guide
- `developer.android.com/privacy-and-security/security-config` — network_security_config.xml format
- `developer.android.com/jetpack/androidx/releases/security` — EncryptedSharedPreferences deprecation confirmed (1.1.0-beta01, June 2025)

### Secondary (MEDIUM confidence)
- `medium.com/@mohammad.hasan.mahdavi81` — DataStore + Android Keystore encryption pattern (verified against official crypto docs)
- `aeshirey.github.io/code/2024/12/01/simple-axum-server-with-tls.html` — Simple axum TLS example (aligns with official axum example)

### Tertiary (LOW confidence)
- Community articles on EncryptedSharedPreferences migration — consistent with official deprecation notice, not independently verified for completeness

## Metadata

**Confidence breakdown:**
- Standard stack (Rust TLS): HIGH — axum-server 0.8 docs read directly; axum official example inspected
- Standard stack (Android crypto): HIGH — official Android developer docs + confirmed deprecation of ESP
- Architecture patterns: HIGH — patterns derived directly from official documentation
- Pitfalls: MEDIUM-HIGH — pitfalls 1-3 from official docs; pitfalls 4-6 from reasoned analysis of the code (verified against known Android Keystore behavior)
- aws-lc-rs build concern: MEDIUM — documented behavior of rustls 0.23 default backend; system-specific outcome unknown

**Research date:** 2026-03-31
**Valid until:** 2026-07-01 (axum-server and Android APIs are stable; EncryptedSharedPreferences deprecation path is confirmed)
