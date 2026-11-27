-- Fields recorded by Entire CLI 0.13.2+: prompt-cache token usage, the model and the number of turns.
-- (0.13.x no longer writes initial_attribution, so the attribution columns stay empty for new data.)

ALTER TABLE `session`
    ADD COLUMN `cache_creation_tokens` BIGINT NULL COMMENT 'Token usage: prompt-cache writes' AFTER `output_tokens`,
    ADD COLUMN `cache_read_tokens`     BIGINT NULL COMMENT 'Token usage: prompt-cache reads' AFTER `cache_creation_tokens`,
    ADD COLUMN `model`                 VARCHAR(128) NULL COMMENT 'Model used by the agent, e.g. claude-opus-5-5' AFTER `agent`,
    ADD COLUMN `turn_count`            INTEGER NULL COMMENT 'session_metrics.turn_count: prompts in the session' AFTER `api_call_count`;

-- Forget the per-branch sync position so the next sync re-reads every checkpoint and backfills the new columns
DELETE FROM `checkpoint_sync_state`;
