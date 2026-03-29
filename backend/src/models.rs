use serde::{Deserialize, Serialize};

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct TmuxSession {
    pub id: String,
    pub name: String,
    pub created_at: String,
    pub attached: bool,
}

#[derive(Debug, Serialize, Deserialize, Clone, sqlx::FromRow)]
pub struct Command {
    pub id: String,
    pub name: String,
    pub command: String,
    pub description: Option<String>,
    pub category: String,
    pub icon: Option<String>,
    pub created_at: String,
    pub updated_at: String,
}

#[derive(Debug, Deserialize)]
pub struct CreateCommand {
    pub name: String,
    pub command: String,
    pub description: Option<String>,
    pub category: String,
    pub icon: Option<String>,
}

#[derive(Debug, Deserialize)]
pub struct UpdateCommand {
    pub name: Option<String>,
    pub command: Option<String>,
    pub description: Option<String>,
    pub category: Option<String>,
    pub icon: Option<String>,
}

#[derive(Debug, Deserialize)]
pub struct CreateSession {
    pub name: Option<String>,
}

#[derive(Debug, Deserialize)]
pub struct ExecCommand {
    pub command_id: String,
}
