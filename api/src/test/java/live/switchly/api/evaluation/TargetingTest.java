package live.switchly.api.evaluation;

import live.switchly.api.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class TargetingTest {
    private static final EvaluationContext CONTEXT = EvaluationContext.parse(json("""
            {"email":"dev@example.com","country":"IN","custom":{"count":4,"paid":true}}
            """));

    private static JsonNode json(String text) { return FlagValueValidator.getMapper().readTree(text); }
    private static ConditionGroup group(String condition) {
        return ConditionGroup.parse(json("{\"match\":\"AND\",\"conditions\":[" + condition + "]}"));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "country|EQUALS|\"IN\"|true", "country|NOT_EQUALS|\"US\"|true", "email|CONTAINS|\"@\"|true",
            "email|STARTS_WITH|\"dev\"|true", "email|ENDS_WITH|\".com\"|true", "custom.count|GREATER_THAN|3|true",
            "custom.count|GREATER_THAN_OR_EQUAL|4|true", "custom.count|LESS_THAN|4|false",
            "custom.count|LESS_THAN_OR_EQUAL|4|true", "country|IN|[\"IN\",\"US\"]|true",
            "country|NOT_IN|[\"US\"]|true", "platform|NOT_IN|[\"ios\"]|false", "platform|NOT_EQUALS|\"ios\"|false",
            "custom.count|NOT_EQUALS|\"4\"|false", "custom.count|IN|[4.0]|true", "custom.paid|EQUALS|true|true"})
    void typedOperators(String attribute, String op, String value, boolean matches) {
        var condition = group("""
                {"attribute":"%s","operator":"%s","value":%s}
                """.formatted(attribute, op, value));
        assertThat(condition.matches(CONTEXT)).isEqualTo(matches);
    }

    @Test
    void presenceGroupsAndPriority() {
        String conditions = """
                [{"attribute":"country","operator":"EXISTS"},{"attribute":"country","operator":"NOT_EXISTS"}]
                """;
        var all = ConditionGroup.parse(json("{\"match\":\"AND\",\"conditions\":" + conditions + "}"));
        var any = ConditionGroup.parse(json("{\"match\":\"OR\",\"conditions\":" + conditions + "}"));
        assertThat(all.matches(CONTEXT)).isFalse();
        assertThat(any.matches(CONTEXT)).isTrue();
        assertThat(group("{\"attribute\":\"device\",\"operator\":\"NOT_EXISTS\"}").matches(CONTEXT)).isTrue();
        UUID project = UUID.randomUUID();
        var env = new Environment(UUID.randomUUID(), project, "prod", "Prod", null, true);
        var flag = new Flag(UUID.randomUUID(), null, project, "x", "X");
        var config = new FlagEnvironmentConfig(UUID.randomUUID(), flag.getId(), env.getId(), project, true, json("true"));
        var deny = new TargetingRule(TargetingRule.Action.DEFAULT, any);
        var allow = new TargetingRule(TargetingRule.Action.SERVE, any);
        var miss = new TargetingRule(TargetingRule.Action.SERVE, all);
        assertThat(FlagEvaluationEngine.evaluate(flag, env, config, CONTEXT, List.of(deny, allow), null, Instant.EPOCH).reason())
                .isEqualTo(EvaluationReason.TARGETING_DEFAULT);
        assertThat(FlagEvaluationEngine.evaluate(flag, env, config, CONTEXT, List.of(miss, allow, deny), null, Instant.EPOCH).reason())
                .isEqualTo(EvaluationReason.TARGETING_MATCH);
        var result = FlagEvaluationEngine.evaluate(flag, env, config, CONTEXT, List.of(miss), null, Instant.EPOCH);
        assertThat(result.reason()).isEqualTo(EvaluationReason.TARGETING_NO_MATCH);
        assertThat(result.effectiveValue()).isEqualTo(json("false"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"attribute\":\"userId\",\"operator\":\"GREATER_THAN\",\"value\":1}",
        "{\"attribute\":\"custom.count\",\"operator\":\"LESS_THAN\",\"value\":\"2\"}",
        "{\"attribute\":\"custom.foo\",\"operator\":\"IN\",\"value\":[1,\"2\"]}",
        "{\"attribute\":\"country\",\"operator\":\"EXISTS\",\"value\":null}",
        "{\"attribute\":\"unknown\",\"operator\":\"EXISTS\"}",
        "{\"attribute\":\"country\",\"operator\":\"CONTAINS\",\"value\":42}",
        "{\"attribute\":\"custom.a\",\"operator\":\"IN\",\"value\":[]}",
        "{\"attribute\":\"country\",\"operator\":\"LIKE\",\"value\":\"IN\"}",
        "{\"attribute\":\"country\",\"operator\":\"EXISTS\",\"extra\":true}"})
    void rejectsIncompatibleConfigurations(String condition) {
        assertThatIllegalArgumentException().isThrownBy(() -> group(condition));
    }
}
