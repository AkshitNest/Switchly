package live.switchly.api.evaluation;

import live.switchly.api.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static live.switchly.api.evaluation.EvaluationReason.*;

class PercentageRolloutTest {
    private final UUID flagId = new UUID(0, 1);
    private final UUID envId = new UUID(0, 2);

    @Test
    void publishedVectorAndEnvironmentIndependence() {
        assertThat(PercentageRollout.bucket(flagId, envId, "user-123")).isEqualTo(3031);
        assertThat(PercentageRollout.bucket(flagId, envId, "user-123")).isEqualTo(3031);
        assertThat(PercentageRollout.bucket(flagId, new UUID(0, 3), "user-123")).isNotEqualTo(3031);
        assertThat(PercentageRollout.bucket(new UUID(0, 4), envId, "user-123")).isNotEqualTo(3031);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 10, 50, 100})
    void stableCohortsAndBoundaries(int pct) {
        int included = 0;
        for (int i = 0; i < 10_000; i++) {
            String user = "identity-" + i;
            boolean eligible = PercentageRollout.decide(flagId, envId, user, pct) == ROLLOUT_INCLUDED;
            assertThat(eligible).isEqualTo(PercentageRollout.bucket(flagId, envId, user) < pct * 100);
            if (eligible) included++;
        }
        assertThat(included).isCloseTo(pct * 100, within(pct == 0 || pct == 100 ? 0 : 100));
        assertThat(PercentageRollout.decide(flagId, envId, null, pct)).isEqualTo(ROLLOUT_MISSING_IDENTITY);
    }

    @Test
    void targetingAndStateGateRollout() {
        UUID project = UUID.randomUUID();
        Flag flag = new Flag(flagId, null, project, "beta", "Beta");
        var env = new Environment(envId, project, "prod", "Prod", null, true);
        var mapper = FlagValueValidator.getMapper();
        var config = new FlagEnvironmentConfig(UUID.randomUUID(), flagId, envId, project, true, mapper.readTree("true"));
        var context = EvaluationContext.parse(mapper.readTree("{\"userId\":\"user-123\"}"));
        var group = ConditionGroup.parse(mapper.readTree("""
                {"match":"AND","conditions":[{"attribute":"userId","operator":"EXISTS"}]}
                """));
        var allow = List.of(new TargetingRule(TargetingRule.Action.SERVE, group));
        var deny = List.of(new TargetingRule(TargetingRule.Action.DEFAULT, group));
        assertThat(FlagEvaluationEngine.evaluate(flag, env, config, context, allow, 30, Instant.EPOCH).reason()).isEqualTo(ROLLOUT_EXCLUDED);
        assertThat(FlagEvaluationEngine.evaluate(flag, env, config, context, allow, 31, Instant.EPOCH).reason()).isEqualTo(ROLLOUT_INCLUDED);
        assertThat(FlagEvaluationEngine.evaluate(flag, env, config, context, deny, 100, Instant.EPOCH).reason()).isEqualTo(TARGETING_DEFAULT);
        config.setEnabled(false);
        assertThat(FlagEvaluationEngine.evaluate(flag, env, config, context, allow, 100, Instant.EPOCH).reason()).isEqualTo(FLAG_DISABLED_DEFAULT);
    }
}
