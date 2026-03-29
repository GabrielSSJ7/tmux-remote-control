use crate::models::TmuxSession;
use tokio::process::Command;

pub struct TmuxManager;

impl TmuxManager {
    pub fn parse_list_sessions(output: &str) -> Vec<TmuxSession> {
        output
            .lines()
            .filter(|line| !line.is_empty())
            .filter_map(|line| {
                let parts: Vec<&str> = line.splitn(4, ':').collect();
                if parts.len() < 4 {
                    return None;
                }
                Some(TmuxSession {
                    id: parts[0].trim().to_string(),
                    name: parts[1].trim().to_string(),
                    created_at: parts[2].trim().to_string(),
                    attached: parts[3].trim() == "1",
                })
            })
            .collect()
    }

    pub async fn list_sessions() -> Result<Vec<TmuxSession>, String> {
        let output = Command::new("tmux")
            .args(["list-sessions", "-F", "#{session_id}:#{session_name}:#{session_created}:#{session_attached}"])
            .output()
            .await
            .map_err(|e| format!("Failed to run tmux: {}", e))?;

        if !output.status.success() {
            let stderr = String::from_utf8_lossy(&output.stderr);
            if stderr.contains("no server running") || stderr.contains("no sessions") {
                return Ok(vec![]);
            }
            return Err(format!("tmux error: {}", stderr));
        }

        let stdout = String::from_utf8_lossy(&output.stdout);
        Ok(Self::parse_list_sessions(&stdout))
    }

    pub async fn create_session(name: &str, shell: &str) -> Result<TmuxSession, String> {
        let output = Command::new("tmux")
            .args(["new-session", "-d", "-s", name, shell])
            .output()
            .await
            .map_err(|e| format!("Failed to create session: {}", e))?;

        if !output.status.success() {
            return Err(format!("tmux error: {}", String::from_utf8_lossy(&output.stderr)));
        }

        let sessions = Self::list_sessions().await?;
        sessions
            .into_iter()
            .find(|s| s.name == name)
            .ok_or_else(|| "Session created but not found in list".to_string())
    }

    pub async fn kill_session(name: &str) -> Result<(), String> {
        let output = Command::new("tmux")
            .args(["kill-session", "-t", name])
            .output()
            .await
            .map_err(|e| format!("Failed to kill session: {}", e))?;

        if !output.status.success() {
            return Err(format!("tmux error: {}", String::from_utf8_lossy(&output.stderr)));
        }
        Ok(())
    }

    pub async fn send_keys(session: &str, keys: &str) -> Result<(), String> {
        let output = Command::new("tmux")
            .args(["send-keys", "-t", session, keys, "Enter"])
            .output()
            .await
            .map_err(|e| format!("Failed to send keys: {}", e))?;

        if !output.status.success() {
            return Err(format!("tmux error: {}", String::from_utf8_lossy(&output.stderr)));
        }
        Ok(())
    }

    pub async fn session_exists(name: &str) -> bool {
        Command::new("tmux")
            .args(["has-session", "-t", name])
            .output()
            .await
            .map(|o| o.status.success())
            .unwrap_or(false)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parse_list_sessions_valid_output() {
        let output = "$1:main:1711720000:1\n$2:work:1711720100:0\n";
        let sessions = TmuxManager::parse_list_sessions(output);
        assert_eq!(sessions.len(), 2);
        assert_eq!(sessions[0].name, "main");
        assert!(sessions[0].attached);
        assert_eq!(sessions[1].name, "work");
        assert!(!sessions[1].attached);
    }

    #[test]
    fn parse_list_sessions_empty_output() {
        let sessions = TmuxManager::parse_list_sessions("");
        assert!(sessions.is_empty());
    }

    #[test]
    fn parse_list_sessions_malformed_line() {
        let output = "invalid-line\n$1:valid:123:0\n";
        let sessions = TmuxManager::parse_list_sessions(output);
        assert_eq!(sessions.len(), 1);
        assert_eq!(sessions[0].name, "valid");
    }
}
