package live.switchly.api.controller;

import jakarta.validation.Valid;
import live.switchly.api.dto.AddMemberRequest;
import live.switchly.api.dto.OrganizationMemberResponse;
import live.switchly.api.dto.UpdateMemberRoleRequest;
import live.switchly.api.exception.UnauthorizedException;
import live.switchly.api.security.UserPrincipal;
import live.switchly.api.service.OrganizationMembershipService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orgs/{orgId}/members")
public class OrganizationMembershipController {

    private final OrganizationMembershipService membershipService;

    public OrganizationMembershipController(OrganizationMembershipService membershipService) {
        this.membershipService = membershipService;
    }

    @GetMapping
    public List<OrganizationMemberResponse> listMembers(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID orgId
    ) {
        requireAuthentication(principal);
        return membershipService.listMembers(orgId, principal.id());
    }

    @GetMapping("/invitations")
    public List<OrganizationMembershipService.Pending> pending(@PathVariable UUID orgId, @AuthenticationPrincipal UserPrincipal user) {
        return membershipService.pending(orgId, user.id());
    }

    public record CancelInvite(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Email String email) {}

    @PostMapping("/invitations/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable UUID orgId, @AuthenticationPrincipal UserPrincipal user, @Valid @RequestBody CancelInvite body) {
        membershipService.cancel(orgId, user.id(), body.email());
    }

    public record InviteResponse(String token) {}

    @PostMapping("/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public InviteResponse invite(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID orgId,
            @Valid @RequestBody AddMemberRequest request
    ) {
        requireAuthentication(principal);
        return new InviteResponse(membershipService.invite(orgId, principal.id(), request.email(), request.role(), request.projectId()));
    }

    @PutMapping("/{userId}/role")
    public OrganizationMemberResponse updateMemberRole(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID orgId,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateMemberRoleRequest request
    ) {
        requireAuthentication(principal);
        return membershipService.updateRole(orgId, principal.id(), userId, request.role(), request.projectId());
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID orgId,
            @PathVariable UUID userId
    ) {
        requireAuthentication(principal);
        membershipService.removeMember(orgId, principal.id(), userId);
    }

    private static void requireAuthentication(UserPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException("Full authentication is required to access this resource");
        }
    }
}

