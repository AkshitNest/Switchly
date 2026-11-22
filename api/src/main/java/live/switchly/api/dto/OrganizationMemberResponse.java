package live.switchly.api.dto;

import live.switchly.api.model.OrganizationMembership;
import live.switchly.api.model.Role;
import live.switchly.api.model.User;

import java.time.Instant;
import java.util.UUID;

public record OrganizationMemberResponse(
        UUID id,
        UUID userId,
        String fullName,
        String email,
        Role role,
        UUID projectId,
        Instant memberSince
) {
    public static OrganizationMemberResponse from(OrganizationMembership membership, User user) {
        return new OrganizationMemberResponse(
                membership.getId(),
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                membership.getRole(),
                membership.getProjectId(),
                membership.getCreatedAt()
        );
    }
}

