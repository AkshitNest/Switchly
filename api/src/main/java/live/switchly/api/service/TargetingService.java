package live.switchly.api.service;

import live.switchly.api.evaluation.*;
import live.switchly.api.model.*;
import live.switchly.api.repository.SegmentRepository;
import live.switchly.api.repository.TargetingRepository;
import live.switchly.api.security.TenantAuthorizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class TargetingService {
    private final TenantAuthorizationService auth;
    private final SegmentRepository segments;
    private final TargetingRepository targeting;

    public TargetingService(TenantAuthorizationService auth, SegmentRepository segments, TargetingRepository targeting) {
        this.auth = auth;
        this.segments = segments;
        this.targeting = targeting;
    }

    public List<UserSegment> list(UUID project, UUID user) {
        auth.validateProjectAccess(project, user, Role.EMPLOYEE);
        return segments.list(project);
    }

    public UserSegment get(UUID project, String key, UUID user) {
        auth.validateProjectAccess(project, user, Role.EMPLOYEE);
        return segments.get(project, key);
    }

    @Transactional
    public UserSegment save(UUID project, String key, String name, String description, JsonNode conditions, boolean create, UUID user) {
        auth.validateProjectAccess(project, user, Role.EMPLOYEE);
        return segments.save(project, key, name, description, ConditionGroup.parse(conditions), create);
    }

    @Transactional
    public void delete(UUID project, String key, UUID user) {
        auth.validateProjectAccess(project, user, Role.EMPLOYEE);
        segments.delete(project, key);
    }

    public List<RuleDefinition> rules(UUID env, UUID flag, UUID user) {
        var config = auth.validateFlagEnvironmentAccess(env, flag, user, Role.EMPLOYEE);
        return targeting.get(config.getProjectId(), config.getId());
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public FlagEvaluationResult preview(UUID env, UUID flag, UUID user, EvaluationContext context) {
        var environment = auth.validateEnvironmentAccess(env, user);
        var definition = auth.validateFlagAccess(flag, user);
        var config = auth.validateFlagEnvironmentAccess(env, flag, user, Role.EMPLOYEE);
        var rules = targeting.resolve(config.getProjectId(), List.of(config.getId()));
        return FlagEvaluationEngine.evaluate(definition, environment, config, context,
                rules.getOrDefault(config.getId(), List.of()), targeting.rollout(config.getId()), java.time.Instant.now());
    }

    public Integer rollout(UUID env, UUID flag, UUID user) {
        var config = auth.validateFlagEnvironmentAccess(env, flag, user, Role.EMPLOYEE);
        return targeting.rollout(config.getId());
    }

    @Transactional
    public Integer setRollout(UUID env, UUID flag, JsonNode input, UUID user) {
        var config = auth.validateFlagEnvironmentAccess(env, flag, user, Role.EMPLOYEE);
        ConditionGroup.fields(input, java.util.Set.of("percentage"));
        JsonNode value = input.get("percentage");
        if (value == null || !(value.isNull() || (value.isIntegralNumber() && value.canConvertToInt()
                && value.intValue() >= 0 && value.intValue() <= 100))) {
            throw new IllegalArgumentException("percentage must be null or an integer from 0 to 100");
        }
        Integer percentage = value.isNull() ? null : value.intValue();
        targeting.setRollout(config.getId(), percentage);
        return percentage;
    }

    @Transactional
    public List<RuleDefinition> replace(UUID env, UUID flag, JsonNode input, UUID user) {
        var config = auth.validateFlagEnvironmentAccess(env, flag, user, Role.EMPLOYEE);
        var rules = RuleDefinition.parseRules(input);
        targeting.replace(config, rules);
        return rules;
    }
}
