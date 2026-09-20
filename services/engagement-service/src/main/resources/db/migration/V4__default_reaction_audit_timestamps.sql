-- Allow inserts from the previous application version during a rolling deployment.
-- Updated applications continue to supply their explicit occurrence timestamps.
ALTER TABLE reactions
    ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP,
    ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP;
