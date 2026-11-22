package live.switchly.api.controller;

import jakarta.validation.Valid;
import live.switchly.api.dto.CreateProjectRequest;
import live.switchly.api.dto.ProjectResponse;
import live.switchly.api.exception.UnauthorizedException;
import live.switchly.api.security.UserPrincipal;
import live.switchly.api.service.ProjectService;
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
@RequestMapping("/api/v1")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping("/orgs/{orgId}/projects")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse create(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID orgId,
            @Valid @RequestBody CreateProjectRequest request
    ) {
        requireAuthentication(principal);
        return ProjectResponse.from(projectService.create(orgId, request.name(), request.key(), principal.id()));
    }

    @GetMapping("/orgs/{orgId}/projects")
    public List<ProjectResponse> getAllForOrganization(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID orgId
    ) {
        requireAuthentication(principal);
        return projectService.getAllForOrganization(orgId, principal.id()).stream()
                .map(ProjectResponse::from)
                .toList();
    }

    @GetMapping("/projects/{projectId}")
    public ProjectResponse getById(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID projectId
    ) {
        requireAuthentication(principal);
        return ProjectResponse.from(projectService.getById(projectId, principal.id()));
    }

    private static void requireAuthentication(UserPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException("Full authentication is required to access this resource");
        }
    }
}


