-- Entire CLI 0.13+ uses 26-char ULID checkpoint ids (legacy ids are 12 hex chars)

ALTER TABLE `checkpoint`
    MODIFY COLUMN `checkpoint_id` VARCHAR(32) NOT NULL COMMENT 'Checkpoint identifier (12-char hex legacy id or 26-char ULID)';
