package live.switchly.api.evaluation;

import live.switchly.api.model.Environment;
import live.switchly.api.model.Flag;
import live.switchly.api.model.FlagEnvironmentConfig;
import live.switchly.api.model.FlagValueValidator;
import java.time.Instant;
import java.util.List;

public final class FlagEvaluationEngine {
    private FlagEvaluationEngine() {}

    public static FlagEvaluationResult evaluate(Flag flag, Environment environment, FlagEnvironmentConfig config,
                                                EvaluationContext context, List<TargetingRule> rules,
                                                Integer rolloutPercentage, Instant evaluatedAt) {
        if (!flag.getId().equals(config.getFlagId()) || !environment.getId().equals(config.getEnvironmentId())
                || !flag.getProjectId().equals(environment.getProjectId())
                || !flag.getProjectId().equals(config.getProjectId())) {
            throw new IllegalStateException("Inconsistent evaluation scope");
        }
        try {
            FlagValueValidator.validate(flag.getValueType(), flag.getDefaultValue());
            if (config.isEnabled()) FlagValueValidator.validate(flag.getValueType(), config.getValue());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Invalid stored flag value", ex);
        }
        EvaluationReason reason = decide(config, context, rules);
        boolean serve = reason == EvaluationReason.FLAG_ENABLED || reason == EvaluationReason.TARGETING_MATCH;
        if (serve && rolloutPercentage != null) {
            reason = PercentageRollout.decide(flag.getId(), environment.getId(), context.userId(), rolloutPercentage);
            serve = reason == EvaluationReason.ROLLOUT_INCLUDED;
        }
        return new FlagEvaluationResult(flag.getId(), flag.getKey(), environment.getId(), environment.getKey(),
                flag.getValueType(), serve ? config.getValue() : flag.getDefaultValue(), config.isEnabled(), reason, evaluatedAt);
    }

    private static EvaluationReason decide(FlagEnvironmentConfig config, EvaluationContext context, List<TargetingRule> rules) {
        if (!config.isEnabled()) return EvaluationReason.FLAG_DISABLED_DEFAULT;
        for (TargetingRule rule : rules) {
            if (rule.conditions().matches(context)) {
                return rule.action() == TargetingRule.Action.SERVE ? EvaluationReason.TARGETING_MATCH : EvaluationReason.TARGETING_DEFAULT;
            }
        }
        return rules.isEmpty() ? EvaluationReason.FLAG_ENABLED : EvaluationReason.TARGETING_NO_MATCH;
    }
}
