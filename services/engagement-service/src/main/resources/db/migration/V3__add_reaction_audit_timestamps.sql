ALTER TABLE reactions
    ADD COLUMN created_at TIMESTAMP(6) WITH TIME ZONE,
    ADD COLUMN updated_at TIMESTAMP(6) WITH TIME ZONE;

UPDATE reactions
SET created_at = CURRENT_TIMESTAMP,
    updated_at = CURRENT_TIMESTAMP
WHERE created_at IS NULL
   OR updated_at IS NULL;

ALTER TABLE reactions
    ALTER COLUMN created_at SET NOT NULL,
    ALTER COLUMN updated_at SET NOT NULL;
