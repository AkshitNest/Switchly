package live.switchly.api.service;

import live.switchly.api.evaluation.*;
import live.switchly.api.exception.ForbiddenException;
import live.switchly.api.exception.NotFoundException;
import live.switchly.api.repository.EvaluationRepository;
import live.switchly.api.repository.TargetingRepository;
import live.switchly.api.security.SdkPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FlagEvaluationService {
    private final EvaluationRepository flags;
    private final TargetingRepository targeting;
    public FlagEvaluationService(EvaluationRepository flags, TargetingRepository targeting) {
        this.flags = flags;
        this.targeting = targeting;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<FlagEvaluationResult> evaluate(SdkPrincipal principal, String projectKey, String environmentKey,
                                               List<String> keys, EvaluationContext context) {
        if (!principal.projectKey().equals(projectKey)) throw new ForbiddenException("SDK key does not grant this scope");
        var environment = flags.permittedEnvironment(principal, environmentKey);
        var loaded = flags.load(environment, keys);
        if (loaded.size() != keys.size()) throw new NotFoundException("Requested flag or configuration not found");
        var byKey = loaded.stream().collect(Collectors.toMap(row -> row.flag().getKey(), Function.identity()));
        var enabledConfigs = loaded.stream().filter(row -> row.config().isEnabled()).map(row -> row.config().getId()).toList();
        Map<UUID, List<TargetingRule>> rules = enabledConfigs.isEmpty() ? Map.of() : targeting.resolve(principal.projectId(), enabledConfigs);
        Instant now = Instant.now();
        return keys.stream().map(byKey::get).map(row -> FlagEvaluationEngine.evaluate(row.flag(), environment,
                row.config(), context, rules.getOrDefault(row.config().getId(), List.of()), row.percentage(), now)).toList();
    }
}
