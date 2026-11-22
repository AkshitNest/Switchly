-- Switchly Environment Model Schema Migration
-- Sets up environments table linked to projects with composite uniqueness on (project_id, key)

CREATE TABLE environments (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id  UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    key         VARCHAR(255) NOT NULL,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    is_default  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_environments_project_key UNIQUE (project_id, key)
);

CREATE INDEX idx_environments_project_id ON environments(project_id);

CREATE TRIGGER update_environments_updated_at
    BEFORE UPDATE ON environments
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Backfill default environments (development, staging, production) for pre-existing projects
INSERT INTO environments (project_id, key, name, description, is_default)
SELECT id, 'development', 'Development', 'Development environment', true FROM projects
ON CONFLICT (project_id, key) DO NOTHING;

INSERT INTO environments (project_id, key, name, description, is_default)
SELECT id, 'staging', 'Staging', 'Staging environment', true FROM projects
ON CONFLICT (project_id, key) DO NOTHING;

INSERT INTO environments (project_id, key, name, description, is_default)
SELECT id, 'production', 'Production', 'Production environment', true FROM projects
ON CONFLICT (project_id, key) DO NOTHING;

