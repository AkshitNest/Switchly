package live.switchly.api.evaluation;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public record ConditionGroup(Match match, List<Condition> conditions) {
    public enum Match { AND, OR }
    public enum Operator {
        EQUALS, NOT_EQUALS, CONTAINS, STARTS_WITH, ENDS_WITH, GREATER_THAN, GREATER_THAN_OR_EQUAL,
        LESS_THAN, LESS_THAN_OR_EQUAL, IN, NOT_IN, EXISTS, NOT_EXISTS
    }

    public ConditionGroup {
        conditions = List.copyOf(conditions);
    }

    public static ConditionGroup parse(JsonNode input) {
        fields(input, Set.of("match", "conditions"));
        Match match = enumeration(Match.class, input.get("match"));
        JsonNode list = input.get("conditions");
        if (list == null || !list.isArray() || list.isEmpty() || list.size() > 32) {
            throw new IllegalArgumentException("conditions must contain 1 to 32 conditions");
        }
        List<Condition> conditions = new ArrayList<>();
        for (JsonNode node : list) conditions.add(Condition.parse(node));
        return new ConditionGroup(match, conditions);
    }

    public static void fields(JsonNode node, Set<String> allowed) {
        if (node == null || !node.isObject() || !allowed.containsAll(node.propertyNames())) {
            throw new IllegalArgumentException("Invalid or unexpected object fields");
        }
    }

    public static <E extends Enum<E>> E enumeration(Class<E> type, JsonNode node) {
        if (node != null && node.isString()) {
            for (E value : type.getEnumConstants()) if (value.name().equals(node.stringValue())) return value;
        }
        throw new IllegalArgumentException("Invalid " + type.getSimpleName());
    }

    public boolean matches(EvaluationContext context) {
        return match == Match.AND ? conditions.stream().allMatch(c -> c.matches(context))
                : conditions.stream().anyMatch(c -> c.matches(context));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Condition(String attribute, Operator operator, JsonNode value) {
        static Condition parse(JsonNode node) {
            fields(node, Set.of("attribute", "operator", "value"));
            JsonNode name = node.get("attribute");
            if (name == null || !name.isString() || !EvaluationContext.validAttribute(name.stringValue())) {
                throw new IllegalArgumentException("Invalid condition attribute");
            }
            String attribute = name.stringValue();
            Operator operator = enumeration(Operator.class, node.get("operator"));
            JsonNode value = node.get("value");
            boolean valid = switch (operator) {
                case EXISTS, NOT_EXISTS -> value == null;
                case IN, NOT_IN -> validList(attribute, value);
                case STARTS_WITH, ENDS_WITH, CONTAINS -> validScalar(attribute, value) && value.isString();
                case GREATER_THAN, GREATER_THAN_OR_EQUAL, LESS_THAN, LESS_THAN_OR_EQUAL ->
                        validScalar(attribute, value) && value.isNumber();
                case EQUALS, NOT_EQUALS -> validScalar(attribute, value);
            };
            if (!valid) throw new IllegalArgumentException("Incompatible condition operand for " + operator);
            return new Condition(attribute, operator, value);
        }

        private static boolean validScalar(String name, JsonNode node) {
            return EvaluationContext.isScalar(node) && (!EvaluationContext.isStandard(name) || node.isString());
        }

        private static boolean validList(String attribute, JsonNode node) {
            if (node == null || !node.isArray() || node.isEmpty() || node.size() > 100) return false;
            for (JsonNode item : node) {
                if (!validScalar(attribute, item) || item.getNodeType() != node.get(0).getNodeType()) return false;
            }
            return true;
        }

        boolean matches(EvaluationContext context) {
            JsonNode actual = context.attributes().get(attribute);
            if (operator == Operator.EXISTS) return actual != null;
            if (operator == Operator.NOT_EXISTS) return actual == null;
            boolean list = operator == Operator.IN || operator == Operator.NOT_IN;
            JsonNode sample = list ? value.get(0) : value;
            if (actual == null || actual.getNodeType() != sample.getNodeType()) return false;
            return switch (operator) {
                case EQUALS -> equal(actual, value);
                case NOT_EQUALS -> !equal(actual, value);
                case IN, NOT_IN -> value.valueStream().anyMatch(v -> equal(actual, v)) == (operator == Operator.IN);
                case STARTS_WITH -> actual.stringValue().startsWith(value.stringValue());
                case ENDS_WITH -> actual.stringValue().endsWith(value.stringValue());
                case CONTAINS -> actual.stringValue().contains(value.stringValue());
                case GREATER_THAN -> actual.decimalValue().compareTo(value.decimalValue()) > 0;
                case GREATER_THAN_OR_EQUAL -> actual.decimalValue().compareTo(value.decimalValue()) >= 0;
                case LESS_THAN -> actual.decimalValue().compareTo(value.decimalValue()) < 0;
                case LESS_THAN_OR_EQUAL -> actual.decimalValue().compareTo(value.decimalValue()) <= 0;
                default -> throw new IllegalStateException("Unexpected comparison operator");
            };
        }

        private static boolean equal(JsonNode actual, JsonNode expected) {
            return actual.isNumber() ? actual.decimalValue().compareTo(expected.decimalValue()) == 0 : actual.equals(expected);
        }
    }
}
