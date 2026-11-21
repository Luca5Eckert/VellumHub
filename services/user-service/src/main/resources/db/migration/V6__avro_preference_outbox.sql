ALTER TABLE user_preferences ADD COLUMN avro_revision BIGINT CHECK (avro_revision >= 0);
ALTER TABLE user_preference_outbox ADD COLUMN avro_revision BIGINT CHECK (avro_revision >= 0);

-- NULL denotes a legacy JSON row. A non-null revision stamps the Avro format
-- and remains associated with the row on every retry.
