mod common;

#[tokio::test]
#[ignore]
async fn ws_rejects_non_auth_first_frame() {
    todo!("Send a Data frame as first message, expect rejection SessionEvent and connection close")
}

#[tokio::test]
#[ignore]
async fn ws_accepts_valid_auth_frame() {
    todo!("Send a valid Auth frame as first message, expect connection to proceed")
}

#[tokio::test]
#[ignore]
async fn ws_rejects_invalid_token() {
    todo!("Send Auth frame with wrong token, expect rejection and connection close")
}

#[tokio::test]
#[ignore]
async fn ws_timeout_on_no_first_frame() {
    todo!("Connect but send nothing for >10s, expect disconnect")
}
