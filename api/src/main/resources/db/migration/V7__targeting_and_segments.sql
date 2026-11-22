CREATE TABLE user_segments (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id uuid NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    key varchar(255) NOT NULL,
    name varchar(255) NOT NULL,
    description text,
    conditions jsonb NOT NULL CHECK (jsonb_typeof(conditions) = 'object'),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (project_id, key)
);
CREATE TRIGGER update_user_segments_updated_at BEFORE UPDATE ON user_segments
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

ALTER TABLE flag_environment_configs ADD UNIQUE (id, project_id);
CREATE TABLE targeting_rules (
    config_id uuid NOT NULL,
    project_id uuid NOT NULL,
    priority integer NOT NULL CHECK (priority BETWEEN 0 AND 31),
    action varchar(10) NOT NULL CHECK (action IN ('SERVE','DEFAULT')),
    conditions jsonb CHECK (jsonb_typeof(conditions) = 'object'),
    segment_key varchar(255),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (config_id, priority),
    CHECK (num_nonnulls(conditions, segment_key) = 1),
    FOREIGN KEY (config_id, project_id) REFERENCES flag_environment_configs(id, project_id) ON DELETE CASCADE,
    FOREIGN KEY (project_id, segment_key) REFERENCES user_segments(project_id, key)
);
CREATE INDEX idx_rules_segment ON targeting_rules(project_id, segment_key) WHERE segment_key IS NOT NULL;
CREATE TRIGGER update_targeting_rules_updated_at BEFORE UPDATE ON targeting_rules
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
