package live.switchly.api.service;

import live.switchly.api.exception.ConflictException;
import live.switchly.api.exception.NotFoundException;
import live.switchly.api.model.Environment;
import live.switchly.api.model.Flag;
import live.switchly.api.model.FlagEnvironmentConfig;
import live.switchly.api.model.Role;
import live.switchly.api.repository.EnvironmentRepository;
import live.switchly.api.repository.FlagEnvironmentConfigRepository;
import live.switchly.api.repository.FlagRepository;
import live.switchly.api.security.TenantAuthorizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class EnvironmentService {

    private final ProjectService projectService;
    private final EnvironmentRepository environmentRepository;
    private final FlagRepository flagRepository;
    private final FlagEnvironmentConfigRepository flagEnvironmentConfigRepository;
    private final TenantAuthorizationService tenantAuthorizationService;

    public EnvironmentService(
            ProjectService projectService,
            EnvironmentRepository environmentRepository,
            FlagRepository flagRepository,
            FlagEnvironmentConfigRepository flagEnvironmentConfigRepository,
            TenantAuthorizationService tenantAuthorizationService
    ) {
        this.projectService = projectService;
        this.environmentRepository = environmentRepository;
        this.flagRepository = flagRepository;
        this.flagEnvironmentConfigRepository = flagEnvironmentConfigRepository;
        this.tenantAuthorizationService = tenantAuthorizationService;
    }

    // ── Public User-Facing Secured Operations ──────────────────────────────────

    @Transactional
    public Environment create(UUID projectId, String key, String name, String description, UUID requestingUserId) {
        tenantAuthorizationService.validateProjectAccess(projectId, requestingUserId, Role.ADMIN);

        if (environmentRepository.existsByProjectIdAndKey(projectId, key)) {
            throw new ConflictException("An environment with key '" + key + "' already exists in this project");
        }

        Environment environment = new Environment(UUID.randomUUID(), projectId, key, name, description, false);
        Environment saved = environmentRepository.save(environment);

        // Provision configurations for all existing logical flags in this project
        List<Flag> existingFlags = flagRepository.findByProjectId(projectId);
        for (Flag flag : existingFlags) {
            FlagEnvironmentConfig config = new FlagEnvironmentConfig(
                    UUID.randomUUID(),
                    flag.getId(),
                    saved.getId(),
                    projectId,
                    false,
                    flag.getDefaultValue()
            );
            flagEnvironmentConfigRepository.save(config);
        }

        return saved;
    }

    public Environment getById(UUID id, UUID requestingUserId) {
        tenantAuthorizationService.validateEnvironmentAccess(id, requestingUserId, Role.EMPLOYEE);
        return getById(id);
    }

    public List<Environment> getAllForProject(UUID projectId, UUID requestingUserId) {
        tenantAuthorizationService.validateProjectAccess(projectId, requestingUserId, Role.EMPLOYEE);
        return environmentRepository.findByProjectId(projectId);
    }

    @Transactional
    public Environment update(UUID id, String name, String description, UUID requestingUserId) {
        Environment environment = tenantAuthorizationService.validateEnvironmentAccess(id, requestingUserId, Role.ADMIN);
        environment.setName(name);
        environment.setDescription(description);
        return environmentRepository.save(environment);
    }

    @Transactional
    public void delete(UUID id, UUID requestingUserId) {
        Environment environment = tenantAuthorizationService.validateEnvironmentAccess(id, requestingUserId, Role.ADMIN);
        if (environment.isDefault()) {
            throw new ConflictException("Default environments cannot be deleted");
        }
        environmentRepository.deleteById(id);
    }

    // ── Internal / Package-Private Trusted Operations ─────────────────────────

    @Transactional
    Environment create(UUID projectId, String key, String name, String description) {
        projectService.getById(projectId);

        if (environmentRepository.existsByProjectIdAndKey(projectId, key)) {
            throw new ConflictException("An environment with key '" + key + "' already exists in this project");
        }

        Environment environment = new Environment(UUID.randomUUID(), projectId, key, name, description, false);
        Environment saved = environmentRepository.save(environment);

        List<Flag> existingFlags = flagRepository.findByProjectId(projectId);
        for (Flag flag : existingFlags) {
            FlagEnvironmentConfig config = new FlagEnvironmentConfig(
                    UUID.randomUUID(),
                    flag.getId(),
                    saved.getId(),
                    projectId,
                    false,
                    flag.getDefaultValue()
            );
            flagEnvironmentConfigRepository.save(config);
        }

        return saved;
    }

    Environment getById(UUID id) {
        return environmentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Environment " + id + " not found"));
    }

    List<Environment> getAllForProject(UUID projectId) {
        projectService.getById(projectId);
        return environmentRepository.findByProjectId(projectId);
    }

    @Transactional
    Environment update(UUID id, String name, String description) {
        Environment environment = getById(id);
        environment.setName(name);
        environment.setDescription(description);
        return environmentRepository.save(environment);
    }

    @Transactional
    void delete(UUID id) {
        Environment environment = getById(id);
        if (environment.isDefault()) {
            throw new ConflictException("Default environments cannot be deleted");
        }
        environmentRepository.deleteById(id);
    }
}
