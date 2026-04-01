use serde::{Deserialize, Serialize};
use rand::Rng;
use std::fmt;

#[derive(Debug, Deserialize, Serialize, Clone)]
pub struct Config {
    pub server: ServerConfig,
    pub auth: AuthConfig,
    pub terminal: TerminalConfig,
}

#[derive(Debug, Deserialize, Serialize, Clone)]
pub struct ServerConfig {
    pub host: String,
    pub port: u16,
    #[serde(default)]
    pub allowed_origins: Vec<String>,
}

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

#[derive(Debug, Deserialize, Serialize, Clone)]
pub struct TerminalConfig {
    pub scrollback_lines: u32,
    pub default_shell: String,
}

/// Generates a cryptographically random 64-character hex token.
pub fn generate_token() -> String {
    let mut rng = rand::thread_rng();
    let bytes: Vec<u8> = (0..32).map(|_| rng.gen()).collect();
    hex::encode(bytes)
}

/// Loads config from `path`. If `auth.token` is empty, generates and persists a new token.
pub fn load_config(path: &str) -> Result<Config, Box<dyn std::error::Error>> {
    let content = std::fs::read_to_string(path)?;
    let mut config: Config = toml::from_str(&content)?;
    if config.auth.token.is_empty() {
        config.auth.token = generate_token();
        let updated = toml::to_string_pretty(&config)?;
        std::fs::write(path, updated)?;
        println!("Generated new auth token: [REDACTED]");
    }
    Ok(config)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;

    #[test]
    fn parses_valid_config() {
        let toml_str = r#"
[server]
host = "127.0.0.1"
port = 9999
allowed_origins = ["http://localhost:3000"]

[auth]
token = "abc123"

[terminal]
scrollback_lines = 5000
default_shell = "/bin/zsh"
"#;
        let config: Config = toml::from_str(toml_str).unwrap();
        assert_eq!(config.server.port, 9999);
        assert_eq!(config.auth.token, "abc123");
        assert_eq!(config.terminal.default_shell, "/bin/zsh");
        assert_eq!(config.server.allowed_origins, vec!["http://localhost:3000"]);
    }

    #[test]
    fn parses_config_without_allowed_origins() {
        let toml_str = r#"
[server]
host = "127.0.0.1"
port = 9999

[auth]
token = "abc123"

[terminal]
scrollback_lines = 5000
default_shell = "/bin/zsh"
"#;
        let config: Config = toml::from_str(toml_str).unwrap();
        assert!(config.server.allowed_origins.is_empty());
    }

    #[test]
    fn generate_token_returns_64_hex_chars() {
        let token = generate_token();
        assert_eq!(token.len(), 64);
        assert!(token.chars().all(|c| c.is_ascii_hexdigit()));
    }

    #[test]
    fn load_config_generates_token_if_empty() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("config.toml");
        let mut f = std::fs::File::create(&path).unwrap();
        write!(f, r#"
[server]
host = "0.0.0.0"
port = 48322

[auth]
token = ""

[terminal]
scrollback_lines = 10000
default_shell = "/bin/bash"
"#).unwrap();
        let config = load_config(path.to_str().unwrap()).unwrap();
        assert!(!config.auth.token.is_empty());
        assert_eq!(config.auth.token.len(), 64);
    }

    #[test]
    fn default_bind_address() {
        let toml_str = include_str!("../config.toml.example");
        let config: Config = toml::from_str(toml_str).unwrap();
        assert_eq!(config.server.host, "127.0.0.1");
    }

    #[test]
    fn auth_config_debug_redacted() {
        let auth = AuthConfig { token: "supersecrettoken123".to_string() };
        let debug_output = format!("{:?}", auth);
        assert!(debug_output.contains("[REDACTED]"), "Debug output must contain [REDACTED]");
        assert!(!debug_output.contains("supersecrettoken123"), "Debug output must NOT contain the actual token");
    }

    #[test]
    fn load_config_writes_token_to_disk() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("config.toml");
        let mut f = std::fs::File::create(&path).unwrap();
        write!(f, r#"
[server]
host = "127.0.0.1"
port = 48322

[auth]
token = ""

[terminal]
scrollback_lines = 10000
default_shell = "/bin/bash"
"#).unwrap();
        let config = load_config(path.to_str().unwrap()).unwrap();
        let token = &config.auth.token;
        assert!(!token.is_empty(), "Token should have been generated");
        let source = std::fs::read_to_string(&path).unwrap();
        assert!(source.contains(token), "Token should be written to the config file on disk");
    }
}
