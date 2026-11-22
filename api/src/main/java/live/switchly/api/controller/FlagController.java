package live.switchly.api.controller;

import jakarta.validation.Valid;
import live.switchly.api.dto.CreateFlagRequest;
import live.switchly.api.dto.FlagEnvironmentConfigResponse;
import live.switchly.api.dto.FlagResponse;
import live.switchly.api.dto.UpdateFlagRequest;
import live.switchly.api.dto.UpdateFlagStateRequest;
import live.switchly.api.dto.UpdateFlagValueRequest;
import live.switchly.api.exception.DeprecatedEndpointException;
import live.switchly.api.exception.UnauthorizedException;
import live.switchly.api.security.UserPrincipal;
import live.switchly.api.service.FlagService;
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
@RequestMapping("/api/v1")
public class FlagController {

    private final FlagService flagService;

    public FlagController(FlagService flagService) {
        this.flagService = flagService;
    }

    // ── Project-Scoped Logical Flag Endpoints ─────────────────────────────────

    @PostMapping("/projects/{projectId}/flags")
    @ResponseStatus(HttpStatus.CREATED)
    public FlagResponse create(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateFlagRequest request
    ) {
        requireAuthentication(principal);
        return FlagResponse.from(flagService.create(
                projectId,
                request.key(),
                request.name(),
                request.description(),
                request.valueType(),
                request.defaultValue(),
                principal.id()
        ));
    }

    @GetMapping("/projects/{projectId}/flags")
    public List<FlagResponse> getAllForProject(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID projectId
    ) {
        requireAuthentication(principal);
        return flagService.getAllForProject(projectId, principal.id()).stream()
                .map(FlagResponse::from)
                .toList();
    }

    @GetMapping("/flags/{flagId}")
    public FlagResponse getById(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID flagId
    ) {
        requireAuthentication(principal);
        return FlagResponse.from(flagService.getById(flagId, principal.id()));
    }

    @PutMapping("/flags/{flagId}")
    public FlagResponse update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID flagId,
            @Valid @RequestBody UpdateFlagRequest request
    ) {
        requireAuthentication(principal);
        return FlagResponse.from(flagService.update(
                flagId,
                request.name(),
                request.description(),
                request.defaultValue(),
                principal.id()
        ));
    }

    @DeleteMapping("/flags/{flagId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID flagId
    ) {
        requireAuthentication(principal);
        flagService.delete(flagId, principal.id());
    }

    // ── Environment-Scoped Configuration Endpoints ────────────────────────────

    @GetMapping("/environments/{environmentId}/flags")
    public List<FlagEnvironmentConfigResponse> getConfigsForEnvironment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID environmentId
    ) {
        requireAuthentication(principal);
        return flagService.getConfigsForEnvironment(environmentId, principal.id()).stream()
                .map(FlagEnvironmentConfigResponse::from)
                .toList();
    }

    @GetMapping("/environments/{environmentId}/flags/{flagId}")
    public FlagEnvironmentConfigResponse getConfigForEnvironment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID environmentId,
            @PathVariable UUID flagId
    ) {
        requireAuthentication(principal);
        return FlagEnvironmentConfigResponse.from(flagService.getConfigForEnvironment(environmentId, flagId, principal.id()));
    }

    @PutMapping("/environments/{environmentId}/flags/{flagId}/state")
    public FlagEnvironmentConfigResponse setState(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID environmentId,
            @PathVariable UUID flagId,
            @Valid @RequestBody UpdateFlagStateRequest request
    ) {
        requireAuthentication(principal);
        return FlagEnvironmentConfigResponse.from(flagService.setEnabled(environmentId, flagId, request.enabled(), principal.id()));
    }

    @PutMapping("/environments/{environmentId}/flags/{flagId}/value")
    public FlagEnvironmentConfigResponse setValue(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID environmentId,
            @PathVariable UUID flagId,
            @Valid @RequestBody UpdateFlagValueRequest request
    ) {
        requireAuthentication(principal);
        return FlagEnvironmentConfigResponse.from(flagService.setValue(environmentId, flagId, request.value(), principal.id()));
    }

    // ── Deprecated Legacy Endpoint ────────────────────────────────────────────

    @PutMapping("/flags/{flagId}/state")
    public FlagResponse setLegacyState(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID flagId,
            @Valid @RequestBody UpdateFlagStateRequest request
    ) {
        requireAuthentication(principal);
        throw new DeprecatedEndpointException(
                "Global flag state mutation is deprecated. Use PUT /api/v1/environments/{environmentId}/flags/{flagId}/state"
        );
    }

    private static void requireAuthentication(UserPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException("Full authentication is required to access this resource");
        }
    }
}
