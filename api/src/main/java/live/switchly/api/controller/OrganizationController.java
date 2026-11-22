package live.switchly.api.controller;

import jakarta.validation.Valid;
import live.switchly.api.dto.CreateOrganizationRequest;
import live.switchly.api.dto.OrganizationResponse;
import live.switchly.api.exception.UnauthorizedException;
import live.switchly.api.security.UserPrincipal;
import live.switchly.api.service.OrganizationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orgs")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizationResponse create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateOrganizationRequest request
    ) {
        requireAuthentication(principal);
        organizationService.requireAdmin(principal.organizationId(), principal.id());
        return OrganizationResponse.from(organizationService.create(request.name(), principal.id()));
    }

    @GetMapping
    public List<OrganizationResponse> getAll(@AuthenticationPrincipal UserPrincipal principal) {
        requireAuthentication(principal);
        return organizationService.getAllForUser(principal.id()).stream()
                .map(OrganizationResponse::from)
                .toList();
    }

    @GetMapping("/{orgId}")
    public OrganizationResponse getById(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID orgId
    ) {
        requireAuthentication(principal);
        return OrganizationResponse.from(organizationService.getById(orgId, principal.id()));
    }

    private static void requireAuthentication(UserPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException("Full authentication is required to access this resource");
        }
    }
}

