-- PostgreSQL evaluates the volatile default per existing row without firing UPDATE triggers.
ALTER TABLE projects ADD COLUMN key varchar(255) NOT NULL DEFAULT ('p-' || gen_random_uuid()::text),
    ADD CONSTRAINT uq_projects_org_key UNIQUE (organization_id, key);

CREATE TABLE sdk_keys (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id uuid NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name varchar(255) NOT NULL,
    digest bytea NOT NULL UNIQUE CHECK (octet_length(digest) = 32),
    revoked_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (id, project_id)
);
CREATE INDEX idx_sdk_keys_project ON sdk_keys(project_id);
CREATE TRIGGER update_sdk_keys_updated_at BEFORE UPDATE ON sdk_keys
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TABLE sdk_key_environments (
    key_id uuid NOT NULL,
    project_id uuid NOT NULL,
    environment_id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (key_id, environment_id),
    FOREIGN KEY (key_id, project_id) REFERENCES sdk_keys(id, project_id) ON DELETE CASCADE,
    FOREIGN KEY (environment_id, project_id) REFERENCES environments(id, project_id) ON DELETE CASCADE
);
CREATE INDEX idx_sdk_key_environments_environment ON sdk_key_environments(environment_id);
CREATE TRIGGER update_sdk_key_environments_updated_at BEFORE UPDATE ON sdk_key_environments
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
