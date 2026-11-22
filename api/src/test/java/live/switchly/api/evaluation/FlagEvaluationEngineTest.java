package live.switchly.api.evaluation;

import live.switchly.api.model.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class FlagEvaluationEngineTest {
    final UUID project = UUID.randomUUID();
    final Environment env = new Environment(UUID.randomUUID(), project, "prod", "Prod", null, true);
    final Instant now = Instant.parse("2026-01-01T00:00:00Z");

    static JsonNode json(String value) {
        return FlagValueValidator.getMapper().readTree(value);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"BOOLEAN|false|true", "STRING|\"off\"|\"on\"",
            "NUMBER|7|2.5", "JSON|{}|[false,42]"})
    void preservesTypesAndMetadata(FlagValueType type, String fallback, String configured) {
        Flag flag = new Flag(UUID.randomUUID(), null, project, "feature", "Feature", null, type, json(fallback));
        var config = new FlagEnvironmentConfig(UUID.randomUUID(), flag.getId(), env.getId(), project, false, json(configured));
        for (boolean enabled : new boolean[]{false, true}) {
            config.setEnabled(enabled);
            assertThat(FlagEvaluationEngine.evaluate(flag, env, config, EvaluationContext.parse(null), java.util.List.of(), null, now))
                    .isEqualTo(new FlagEvaluationResult(flag.getId(), "feature", env.getId(), "prod", type,
                            json(enabled ? configured : fallback), enabled,
                            enabled ? EvaluationReason.FLAG_ENABLED : EvaluationReason.FLAG_DISABLED_DEFAULT, now));
        }
    }

    @Test
    void rejectsInconsistentScopeAndCorruptValues() {
        Flag flag = new Flag(UUID.randomUUID(), null, project, "feature", "Feature");
        var badScope = new FlagEnvironmentConfig(UUID.randomUUID(), flag.getId(), UUID.randomUUID(), project, true, json("true"));
        assertThatIllegalStateException().isThrownBy(() -> FlagEvaluationEngine.evaluate(flag, env, badScope, EvaluationContext.parse(null), java.util.List.of(), null, now));
        var badValue = new FlagEnvironmentConfig(UUID.randomUUID(), flag.getId(), env.getId(), project, true, json("1"));
        assertThatIllegalStateException().isThrownBy(() -> FlagEvaluationEngine.evaluate(flag, env, badValue, EvaluationContext.parse(null), java.util.List.of(), null, now));
        badValue.setEnabled(false);
        var disabled = FlagEvaluationEngine.evaluate(flag, env, badValue, EvaluationContext.parse(null), java.util.List.of(), null, now);
        assertThat(disabled.reason()).isEqualTo(EvaluationReason.FLAG_DISABLED_DEFAULT);
        assertThat(disabled.effectiveValue()).isEqualTo(flag.getDefaultValue());
    }
}
