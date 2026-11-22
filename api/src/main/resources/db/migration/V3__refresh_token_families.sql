-- Switchly Refresh Token Families Schema Migration
-- Adds family_id to refresh_tokens for single-use rotation and reuse replay detection

ALTER TABLE refresh_tokens ADD COLUMN family_id UUID;
UPDATE refresh_tokens SET family_id = gen_random_uuid() WHERE family_id IS NULL;
ALTER TABLE refresh_tokens ALTER COLUMN family_id SET NOT NULL;
ALTER TABLE refresh_tokens ALTER COLUMN family_id SET DEFAULT gen_random_uuid();

CREATE INDEX idx_refresh_tokens_family_id ON refresh_tokens(family_id);

