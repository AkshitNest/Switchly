package live.switchly.api.evaluation;

import tools.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public record EvaluationContext(Map<String, JsonNode> attributes) {
    private static final Set<String> STANDARD = Set.of("userId", "email", "country", "device", "platform", "appVersion");
    private static final Pattern CUSTOM_NAME = Pattern.compile("[a-zA-Z][a-zA-Z0-9_]{0,63}");

    public EvaluationContext {
        attributes = Map.copyOf(attributes);
    }

    public static EvaluationContext parse(JsonNode context) {
        Map<String, JsonNode> values = new HashMap<>();
        if (context == null) return new EvaluationContext(values);
        if (!context.isObject()) throw new IllegalArgumentException("context must be an object");
        for (var entry : context.properties()) {
            String name = entry.getKey();
            JsonNode value = entry.getValue();
            if (name.equals("custom")) {
                if (!value.isObject() || value.size() > 64) throw new IllegalArgumentException("Invalid custom attributes");
                for (var attribute : value.properties()) {
                    if (!CUSTOM_NAME.matcher(attribute.getKey()).matches() || !isScalar(attribute.getValue())) {
                        throw new IllegalArgumentException("Invalid custom attribute name or value");
                    }
                    values.put("custom." + attribute.getKey(), attribute.getValue());
                }
            } else {
                if (!STANDARD.contains(name) || !value.isString() || !isScalar(value)
                        || (name.equals("userId") && value.stringValue().isBlank())) {
                    throw new IllegalArgumentException("Invalid evaluation context attribute");
                }
                values.put(name, value);
            }
        }
        return new EvaluationContext(values);
    }

    static boolean isStandard(String name) { return STANDARD.contains(name); }

    static boolean validAttribute(String name) {
        return isStandard(name) || (name.startsWith("custom.") && CUSTOM_NAME.matcher(name.substring(7)).matches());
    }

    static boolean isScalar(JsonNode value) {
        return value != null && (value.isBoolean() || value.isNumber()
                || (value.isString() && value.stringValue().length() <= 1024 && value.stringValue().indexOf(0) < 0));
    }

    public String userId() {
        JsonNode userId = attributes.get("userId");
        return userId == null ? null : userId.stringValue();
    }
}
