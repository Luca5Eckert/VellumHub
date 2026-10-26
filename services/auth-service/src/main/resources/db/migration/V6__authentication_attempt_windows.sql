CREATE TABLE auth_attempt_window (
    remote_hash char(64) NOT NULL,
    window_start bigint NOT NULL,
    attempts integer NOT NULL,
    PRIMARY KEY (remote_hash, window_start)
);
CREATE INDEX idx_auth_attempt_window_expiry ON auth_attempt_window(window_start);
