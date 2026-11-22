ALTER TABLE projects ADD CONSTRAINT uq_project_org UNIQUE (id, organization_id);
ALTER TABLE organization_memberships DROP CONSTRAINT chk_membership_role,
    ADD COLUMN project_id uuid,
    ADD FOREIGN KEY (project_id, organization_id) REFERENCES projects(id, organization_id);
UPDATE organization_memberships SET role = CASE WHEN role IN ('OWNER', 'ADMIN') THEN 'ADMIN' ELSE 'EMPLOYEE' END;
-- Existing employees remain unassigned until an administrator selects a project.
ALTER TABLE organization_memberships ADD CHECK (role IN ('ADMIN', 'EMPLOYEE')),
    ADD CHECK (role = 'EMPLOYEE' OR project_id IS NULL);
ALTER TABLE refresh_tokens ADD COLUMN membership_id uuid REFERENCES organization_memberships(id) ON DELETE CASCADE;
UPDATE refresh_tokens SET revoked = true WHERE revoked = false;
CREATE TABLE organization_invitations (
    token_hash varchar(64) PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    email varchar(255) NOT NULL,
    role varchar(12) NOT NULL CHECK (role IN ('ADMIN','EMPLOYEE')),
    project_id uuid,
    FOREIGN KEY (project_id, organization_id) REFERENCES projects(id, organization_id),
    CHECK ((role = 'EMPLOYEE') = (project_id IS NOT NULL)),
    expires_at timestamptz NOT NULL,
    UNIQUE (organization_id, email)
);
