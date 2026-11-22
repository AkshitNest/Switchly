-- Switchly Performance & Query Optimization Migration
-- Adds missing composite index for OWNER count queries used in last-owner protection.
-- This query is on the hot path for updateRole() and removeMember() under pessimistic lock.

-- Composite index on (organization_id, role) for countByOrganizationIdAndRole()
-- which is called during last-owner invariant enforcement
CREATE INDEX idx_org_memberships_org_role ON organization_memberships(organization_id, role);

-- Partial index on (family_id) WHERE revoked = false for fast active-token lookup
-- Used by countActiveByFamilyId() during token lifecycle management
CREATE INDEX idx_refresh_tokens_family_active ON refresh_tokens(family_id) WHERE revoked = false;

