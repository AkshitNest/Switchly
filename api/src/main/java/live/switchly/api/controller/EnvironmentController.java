package live.switchly.api.controller;

import jakarta.validation.Valid;
import live.switchly.api.dto.CreateEnvironmentRequest;
import live.switchly.api.dto.EnvironmentResponse;
import live.switchly.api.dto.UpdateEnvironmentRequest;
import live.switchly.api.exception.UnauthorizedException;
import live.switchly.api.security.UserPrincipal;
import live.switchly.api.service.EnvironmentService;
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
public class EnvironmentController {

    private final EnvironmentService environmentService;

    public EnvironmentController(EnvironmentService environmentService) {
        this.environmentService = environmentService;
    }

    @PostMapping("/projects/{projectId}/environments")
    @ResponseStatus(HttpStatus.CREATED)
    public EnvironmentResponse create(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateEnvironmentRequest request
    ) {
        requireAuthentication(principal);
        return EnvironmentResponse.from(
                environmentService.create(projectId, request.key(), request.name(), request.description(), principal.id())
        );
    }

    @GetMapping("/projects/{projectId}/environments")
    public List<EnvironmentResponse> getAllForProject(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID projectId
    ) {
        requireAuthentication(principal);
        return environmentService.getAllForProject(projectId, principal.id()).stream()
                .map(EnvironmentResponse::from)
                .toList();
    }

    @GetMapping("/environments/{environmentId}")
    public EnvironmentResponse getById(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID environmentId
    ) {
        requireAuthentication(principal);
        return EnvironmentResponse.from(environmentService.getById(environmentId, principal.id()));
    }

    @PutMapping("/environments/{environmentId}")
    public EnvironmentResponse update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID environmentId,
            @Valid @RequestBody UpdateEnvironmentRequest request
    ) {
        requireAuthentication(principal);
        return EnvironmentResponse.from(
                environmentService.update(environmentId, request.name(), request.description(), principal.id())
        );
    }

    @DeleteMapping("/environments/{environmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID environmentId
    ) {
        requireAuthentication(principal);
        environmentService.delete(environmentId, principal.id());
    }

    private static void requireAuthentication(UserPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException("Full authentication is required to access this resource");
        }
    }
}

