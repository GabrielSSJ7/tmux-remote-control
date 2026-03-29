use serde::{Deserialize, Serialize};

const TYPE_DATA: u8 = 0x00;
const TYPE_RESIZE: u8 = 0x01;
const TYPE_PING: u8 = 0x02;
const TYPE_PONG: u8 = 0x03;
const TYPE_SESSION_EVENT: u8 = 0x04;

#[derive(Debug, PartialEq)]
pub enum Frame {
    Data(Vec<u8>),
    Resize { cols: u16, rows: u16 },
    Ping,
    Pong,
    SessionEvent(SessionEventPayload),
}

#[derive(Debug, Serialize, Deserialize, PartialEq)]
pub struct SessionEventPayload {
    pub event_type: String,
    pub session_id: String,
}

impl Frame {
    pub fn encode(&self) -> Vec<u8> {
        match self {
            Frame::Data(data) => {
                let mut buf = Vec::with_capacity(1 + data.len());
                buf.push(TYPE_DATA);
                buf.extend_from_slice(data);
                buf
            }
            Frame::Resize { cols, rows } => {
                let mut buf = Vec::with_capacity(5);
                buf.push(TYPE_RESIZE);
                buf.extend_from_slice(&cols.to_be_bytes());
                buf.extend_from_slice(&rows.to_be_bytes());
                buf
            }
            Frame::Ping => vec![TYPE_PING],
            Frame::Pong => vec![TYPE_PONG],
            Frame::SessionEvent(payload) => {
                let json = serde_json::to_vec(payload).unwrap();
                let mut buf = Vec::with_capacity(1 + json.len());
                buf.push(TYPE_SESSION_EVENT);
                buf.extend_from_slice(&json);
                buf
            }
        }
    }

    pub fn decode(data: &[u8]) -> Result<Self, String> {
        if data.is_empty() {
            return Err("Empty frame".to_string());
        }
        match data[0] {
            TYPE_DATA => Ok(Frame::Data(data[1..].to_vec())),
            TYPE_RESIZE => {
                if data.len() < 5 {
                    return Err("Resize frame too short".to_string());
                }
                let cols = u16::from_be_bytes([data[1], data[2]]);
                let rows = u16::from_be_bytes([data[3], data[4]]);
                Ok(Frame::Resize { cols, rows })
            }
            TYPE_PING => Ok(Frame::Ping),
            TYPE_PONG => Ok(Frame::Pong),
            TYPE_SESSION_EVENT => {
                let payload: SessionEventPayload = serde_json::from_slice(&data[1..])
                    .map_err(|e| format!("Invalid session event: {}", e))?;
                Ok(Frame::SessionEvent(payload))
            }
            t => Err(format!("Unknown frame type: 0x{:02x}", t)),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn data_frame_roundtrip() {
        let frame = Frame::Data(b"hello terminal".to_vec());
        let encoded = frame.encode();
        assert_eq!(encoded[0], TYPE_DATA);
        let decoded = Frame::decode(&encoded).unwrap();
        assert_eq!(decoded, frame);
    }

    #[test]
    fn resize_frame_roundtrip() {
        let frame = Frame::Resize { cols: 120, rows: 40 };
        let encoded = frame.encode();
        assert_eq!(encoded[0], TYPE_RESIZE);
        assert_eq!(encoded.len(), 5);
        let decoded = Frame::decode(&encoded).unwrap();
        assert_eq!(decoded, frame);
    }

    #[test]
    fn ping_pong_roundtrip() {
        let ping = Frame::Ping;
        assert_eq!(Frame::decode(&ping.encode()).unwrap(), Frame::Ping);
        let pong = Frame::Pong;
        assert_eq!(Frame::decode(&pong.encode()).unwrap(), Frame::Pong);
    }

    #[test]
    fn session_event_roundtrip() {
        let frame = Frame::SessionEvent(SessionEventPayload {
            event_type: "ended".to_string(),
            session_id: "rc-abc123".to_string(),
        });
        let encoded = frame.encode();
        assert_eq!(encoded[0], TYPE_SESSION_EVENT);
        let decoded = Frame::decode(&encoded).unwrap();
        assert_eq!(decoded, frame);
    }

    #[test]
    fn empty_frame_returns_error() {
        assert!(Frame::decode(&[]).is_err());
    }

    #[test]
    fn unknown_type_returns_error() {
        assert!(Frame::decode(&[0xFF]).is_err());
    }

    #[test]
    fn resize_too_short_returns_error() {
        assert!(Frame::decode(&[TYPE_RESIZE, 0x00]).is_err());
    }
}
