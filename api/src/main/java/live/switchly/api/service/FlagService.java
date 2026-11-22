package live.switchly.api.service;

import tools.jackson.databind.JsonNode;
import live.switchly.api.exception.ConflictException;
import live.switchly.api.exception.NotFoundException;
import live.switchly.api.model.Environment;
import live.switchly.api.model.Flag;
import live.switchly.api.model.FlagEnvironmentConfig;
import live.switchly.api.model.FlagValueType;
import live.switchly.api.model.FlagValueValidator;
import live.switchly.api.model.Project;
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
public class FlagService {

    private final ProjectService projectService;
    private final FlagRepository flagRepository;
    private final EnvironmentRepository environmentRepository;
    private final FlagEnvironmentConfigRepository flagEnvironmentConfigRepository;
    private final TenantAuthorizationService tenantAuthorizationService;

    public FlagService(
            ProjectService projectService,
            FlagRepository flagRepository,
            EnvironmentRepository environmentRepository,
            FlagEnvironmentConfigRepository flagEnvironmentConfigRepository,
            TenantAuthorizationService tenantAuthorizationService
    ) {
        this.projectService = projectService;
        this.flagRepository = flagRepository;
        this.environmentRepository = environmentRepository;
        this.flagEnvironmentConfigRepository = flagEnvironmentConfigRepository;
        this.tenantAuthorizationService = tenantAuthorizationService;
    }

    // ── Public User-Facing Secured Operations ──────────────────────────────────

    @Transactional
    public Flag create(UUID projectId, String key, String name, String description,
                       FlagValueType valueType, JsonNode defaultValue, UUID requestingUserId) {
        Project project = tenantAuthorizationService.validateProjectAccess(projectId, requestingUserId, Role.EMPLOYEE);

        if (flagRepository.existsByProjectIdAndKey(projectId, key)) {
            throw new ConflictException("A flag with key '" + key + "' already exists in this project");
        }

        FlagValueType resolvedType = valueType != null ? valueType : FlagValueType.BOOLEAN;
        JsonNode resolvedDefault = defaultValue != null ? defaultValue : FlagValueValidator.defaultFor(resolvedType);
        FlagValueValidator.validate(resolvedType, resolvedDefault);

        Flag flag = new Flag(UUID.randomUUID(), project.getOrganizationId(), projectId, key, name, description, resolvedType, resolvedDefault);
        Flag savedFlag = flagRepository.save(flag);

        // Automatically provision environment configurations for all existing environments of this project
        List<Environment> environments = environmentRepository.findByProjectId(projectId);
        for (Environment env : environments) {
            FlagEnvironmentConfig config = new FlagEnvironmentConfig(
                    UUID.randomUUID(),
                    savedFlag.getId(),
                    env.getId(),
                    projectId,
                    false,
                    savedFlag.getDefaultValue()
            );
            flagEnvironmentConfigRepository.save(config);
        }

        return savedFlag;
    }

    public Flag getById(UUID id, UUID requestingUserId) {
        tenantAuthorizationService.validateFlagAccess(id, requestingUserId, Role.EMPLOYEE);
        return getById(id);
    }

    public List<Flag> getAllForProject(UUID projectId, UUID requestingUserId) {
        tenantAuthorizationService.validateProjectAccess(projectId, requestingUserId, Role.EMPLOYEE);
        return flagRepository.findByProjectId(projectId);
    }

    @Transactional
    public Flag update(UUID flagId, String name, String description, JsonNode defaultValue, UUID requestingUserId) {
        Flag flag = tenantAuthorizationService.validateFlagAccess(flagId, requestingUserId, Role.EMPLOYEE);
        flag.setName(name);
        flag.setDescription(description);
        if (defaultValue != null) {
            FlagValueValidator.validate(flag.getValueType(), defaultValue);
            flag.setDefaultValue(defaultValue);
        }
        return flagRepository.save(flag);
    }

    @Transactional
    public void delete(UUID flagId, UUID requestingUserId) {
        tenantAuthorizationService.validateFlagAccess(flagId, requestingUserId, Role.EMPLOYEE);
        flagEnvironmentConfigRepository.deleteByFlagId(flagId);
        flagRepository.deleteById(flagId);
    }

    // ── Environment-Scoped Configuration Operations ────────────────────────────

    public List<FlagEnvironmentConfig> getConfigsForEnvironment(UUID environmentId, UUID requestingUserId) {
        tenantAuthorizationService.validateEnvironmentAccess(environmentId, requestingUserId, Role.EMPLOYEE);
        return flagEnvironmentConfigRepository.findByEnvironmentId(environmentId);
    }

    public FlagEnvironmentConfig getConfigForEnvironment(UUID environmentId, UUID flagId, UUID requestingUserId) {
        return tenantAuthorizationService.validateFlagEnvironmentAccess(environmentId, flagId, requestingUserId, Role.EMPLOYEE);
    }

    @Transactional
    public FlagEnvironmentConfig setEnabled(UUID environmentId, UUID flagId, boolean enabled, UUID requestingUserId) {
        FlagEnvironmentConfig config = tenantAuthorizationService.validateFlagEnvironmentAccess(environmentId, flagId, requestingUserId, Role.EMPLOYEE);
        config.setEnabled(enabled);
        return flagEnvironmentConfigRepository.save(config);
    }

    @Transactional
    public FlagEnvironmentConfig setValue(UUID environmentId, UUID flagId, JsonNode value, UUID requestingUserId) {
        FlagEnvironmentConfig config = tenantAuthorizationService.validateFlagEnvironmentAccess(environmentId, flagId, requestingUserId, Role.EMPLOYEE);
        Flag flag = flagRepository.findById(flagId)
                .orElseThrow(() -> new NotFoundException("Flag " + flagId + " not found"));
        FlagValueValidator.validate(flag.getValueType(), value);
        config.setValue(value);
        return flagEnvironmentConfigRepository.save(config);
    }

    // ── Internal / Package-Private Trusted Operations ─────────────────────────

    @Transactional
    Flag create(UUID projectId, String key, String name) {
        return create(projectId, key, name, null, FlagValueType.BOOLEAN, null);
    }

    @Transactional
    Flag create(UUID projectId, String key, String name, String description) {
        return create(projectId, key, name, description, FlagValueType.BOOLEAN, null);
    }

    @Transactional
    Flag create(UUID projectId, String key, String name, String description, FlagValueType valueType, JsonNode defaultValue) {
        projectService.getById(projectId);

        if (flagRepository.existsByProjectIdAndKey(projectId, key)) {
            throw new ConflictException("A flag with key '" + key + "' already exists in this project");
        }

        FlagValueType resolvedType = valueType != null ? valueType : FlagValueType.BOOLEAN;
        JsonNode resolvedDefault = defaultValue != null ? defaultValue : FlagValueValidator.defaultFor(resolvedType);
        FlagValueValidator.validate(resolvedType, resolvedDefault);

        Flag flag = new Flag(UUID.randomUUID(), null, projectId, key, name, description, resolvedType, resolvedDefault);
        Flag savedFlag = flagRepository.save(flag);

        List<Environment> environments = environmentRepository.findByProjectId(projectId);
        for (Environment env : environments) {
            FlagEnvironmentConfig config = new FlagEnvironmentConfig(
                    UUID.randomUUID(),
                    savedFlag.getId(),
                    env.getId(),
                    projectId,
                    false,
                    savedFlag.getDefaultValue()
            );
            flagEnvironmentConfigRepository.save(config);
        }

        return savedFlag;
    }

    Flag getById(UUID id) {
        return flagRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Flag " + id + " not found"));
    }

    List<Flag> getAllForProject(UUID projectId) {
        projectService.getById(projectId);
        return flagRepository.findByProjectId(projectId);
    }

    @Transactional
    void delete(UUID flagId) {
        getById(flagId);
        flagEnvironmentConfigRepository.deleteByFlagId(flagId);
        flagRepository.deleteById(flagId);
    }
}
