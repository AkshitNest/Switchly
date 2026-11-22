package live.switchly.api.evaluation;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.JsonNode;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RuleDefinition(TargetingRule.Action action, ConditionGroup conditions, String segmentKey) {
    public static final String KEY_PATTERN = "[a-z0-9][a-z0-9-]{0,254}";
    private static final Pattern KEY = Pattern.compile(KEY_PATTERN);

    public static List<RuleDefinition> parseRules(JsonNode input) {
        if (!input.isArray() || input.size() > 32) throw new IllegalArgumentException("Expected at most 32 targeting rules");
        return input.valueStream().map(RuleDefinition::parse).toList();
    }

    private static RuleDefinition parse(JsonNode node) {
        ConditionGroup.fields(node, Set.of("action", "conditions", "segmentKey"));
        var action = ConditionGroup.enumeration(TargetingRule.Action.class, node.get("action"));
        if (node.has("segmentKey") == node.has("conditions")) {
            throw new IllegalArgumentException("Each rule requires exactly one condition group or segment key");
        }
        if (node.has("conditions")) return new RuleDefinition(action, ConditionGroup.parse(node.get("conditions")), null);
        var key = node.get("segmentKey");
        if (!key.isString() || !KEY.matcher(key.stringValue()).matches()) throw new IllegalArgumentException("Invalid segment key");
        return new RuleDefinition(action, null, key.stringValue());
    }
}
