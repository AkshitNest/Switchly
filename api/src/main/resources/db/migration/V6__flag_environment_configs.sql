-- Switchly Migration V6: Logical Flags, Environment Configurations & Typed Values

-- 1. Ensure composite unique constraints exist on parent tables for cross-project foreign keys
ALTER TABLE flags ADD CONSTRAINT uq_flags_id_project UNIQUE (id, project_id);
ALTER TABLE environments ADD CONSTRAINT uq_environments_id_project UNIQUE (id, project_id);

-- 2. Add typed value metadata to logical flags
ALTER TABLE flags
    ADD COLUMN value_type VARCHAR(32) NOT NULL DEFAULT 'BOOLEAN',
    ADD COLUMN default_value JSONB NOT NULL DEFAULT 'false'::jsonb;

-- 3. Create flag_environment_configs table with composite foreign keys enforcing same-project ownership
CREATE TABLE flag_environment_configs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    flag_id         UUID NOT NULL,
    environment_id  UUID NOT NULL,
    project_id      UUID NOT NULL,
    enabled         BOOLEAN NOT NULL DEFAULT FALSE,
    value           JSONB NOT NULL DEFAULT 'false'::jsonb,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_flag_env_configs_flag_env UNIQUE (flag_id, environment_id),
    CONSTRAINT fk_flag_env_configs_flag FOREIGN KEY (flag_id, project_id)
        REFERENCES flags(id, project_id) ON DELETE CASCADE,
    CONSTRAINT fk_flag_env_configs_env FOREIGN KEY (environment_id, project_id)
        REFERENCES environments(id, project_id) ON DELETE CASCADE,
    CONSTRAINT fk_flag_env_configs_project FOREIGN KEY (project_id)
        REFERENCES projects(id) ON DELETE CASCADE
);

CREATE INDEX idx_flag_env_configs_flag_id ON flag_environment_configs(flag_id);
CREATE INDEX idx_flag_env_configs_env_id ON flag_environment_configs(environment_id);
CREATE INDEX idx_flag_env_configs_project_id ON flag_environment_configs(project_id);

CREATE TRIGGER update_flag_environment_configs_updated_at
    BEFORE UPDATE ON flag_environment_configs
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- 4. Backfill existing flags across all environments belonging to their project
-- Preserves existing enabled state and initializes value accordingly
INSERT INTO flag_environment_configs (flag_id, environment_id, project_id, enabled, value)
SELECT f.id, e.id, f.project_id, f.enabled,
       CASE WHEN f.enabled THEN 'true'::jsonb ELSE f.default_value END
FROM flags f
JOIN environments e ON e.project_id = f.project_id
ON CONFLICT (flag_id, environment_id) DO NOTHING;

-- 5. Drop legacy enabled column from flags table
ALTER TABLE flags DROP COLUMN enabled;

