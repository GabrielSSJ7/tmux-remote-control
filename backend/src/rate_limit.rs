use std::collections::HashMap;
use std::net::IpAddr;
use std::sync::Mutex;
use std::time::{Duration, Instant};

/// IP-based rate limiter with sliding window and consecutive-failure blocking.
pub struct RateLimiter {
    state: Mutex<HashMap<IpAddr, IpState>>,
    max_attempts: u32,
    window: Duration,
    block_duration: Duration,
}

struct IpState {
    attempts: u32,
    window_start: Instant,
    blocked_until: Option<Instant>,
    consecutive_failures: u32,
}

impl RateLimiter {
    /// Creates a new `RateLimiter`.
    ///
    /// # Arguments
    /// * `max_attempts` - Maximum requests allowed within `window_secs`.
    /// * `window_secs` - Sliding window length in seconds.
    /// * `block_secs` - Duration of block applied after 10 consecutive failures.
    pub fn new(max_attempts: u32, window_secs: u64, block_secs: u64) -> Self {
        Self {
            state: Mutex::new(HashMap::new()),
            max_attempts,
            window: Duration::from_secs(window_secs),
            block_duration: Duration::from_secs(block_secs),
        }
    }

    /// Returns `true` if the request from `ip` is allowed, `false` if it should be rejected.
    pub fn check(&self, ip: IpAddr) -> bool {
        let mut state = self.state.lock().unwrap();
        let now = Instant::now();
        let entry = state.entry(ip).or_insert(IpState {
            attempts: 0,
            window_start: now,
            blocked_until: None,
            consecutive_failures: 0,
        });

        if let Some(blocked_until) = entry.blocked_until {
            if now < blocked_until {
                return false;
            }
            entry.blocked_until = None;
            entry.consecutive_failures = 0;
        }

        if now.duration_since(entry.window_start) > self.window {
            entry.attempts = 0;
            entry.window_start = now;
        }

        entry.attempts += 1;
        entry.attempts <= self.max_attempts
    }

    /// Records an authentication failure for `ip`. Blocks the IP after 10 consecutive failures.
    pub fn record_failure(&self, ip: IpAddr) {
        let mut state = self.state.lock().unwrap();
        if let Some(entry) = state.get_mut(&ip) {
            entry.consecutive_failures += 1;
            if entry.consecutive_failures >= 10 {
                entry.blocked_until = Some(Instant::now() + self.block_duration);
            }
        }
    }

    /// Resets the consecutive failure counter for `ip` (called on successful auth).
    pub fn reset(&self, ip: IpAddr) {
        let mut state = self.state.lock().unwrap();
        if let Some(entry) = state.get_mut(&ip) {
            entry.consecutive_failures = 0;
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::net::Ipv4Addr;

    fn localhost() -> IpAddr {
        IpAddr::V4(Ipv4Addr::LOCALHOST)
    }

    #[test]
    fn allows_under_limit() {
        let rl = RateLimiter::new(5, 60, 3600);
        for _ in 0..5 {
            assert!(rl.check(localhost()));
        }
    }

    #[test]
    fn blocks_over_limit() {
        let rl = RateLimiter::new(5, 60, 3600);
        for _ in 0..5 {
            rl.check(localhost());
        }
        assert!(!rl.check(localhost()));
    }

    #[test]
    fn blocks_after_10_consecutive_failures() {
        let rl = RateLimiter::new(100, 60, 3600);
        let ip = localhost();
        rl.check(ip);
        for _ in 0..10 {
            rl.record_failure(ip);
        }
        assert!(!rl.check(ip));
    }

    #[test]
    fn reset_clears_failures() {
        let rl = RateLimiter::new(100, 60, 3600);
        let ip = localhost();
        rl.check(ip);
        for _ in 0..5 {
            rl.record_failure(ip);
        }
        rl.reset(ip);
        assert!(rl.check(ip));
    }
}
