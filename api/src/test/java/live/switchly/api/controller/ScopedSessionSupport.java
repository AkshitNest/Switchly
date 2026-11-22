package live.switchly.api.controller;

import java.util.UUID;
import live.switchly.api.model.User;
import live.switchly.api.repository.OrganizationMembershipRepository;
import live.switchly.api.security.JwtService;
import live.switchly.api.service.OrganizationService;
import org.springframework.beans.factory.annotation.Autowired;

abstract class ScopedSessionSupport {
    @Autowired OrganizationService testOrganizations;
    @Autowired OrganizationMembershipRepository testMembers;
    @Autowired JwtService testJwt;

    String workspaceToken(User user) {
        var memberships = testMembers.findByUserId(user.getId());
        UUID org = memberships.isEmpty() ? testOrganizations.create("Test workspace", user.getId()).getId()
                : memberships.getFirst().getOrganizationId();
        return workspaceToken(user, org.toString());
    }

    String workspaceToken(User user, String org) {
        var membership = testMembers.findByOrganizationIdAndUserId(UUID.fromString(org), user.getId()).orElseThrow();
        return testJwt.generateAccessToken(user, membership.getId());
    }
}
