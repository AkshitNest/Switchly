package live.switchly.api.model;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.BooleanNode;
import tools.jackson.databind.node.IntNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

public final class FlagValueValidator {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(tools.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .build();

    private FlagValueValidator() {}

    public static JsonNode defaultFor(FlagValueType type) {
        if (type == null) {
            type = FlagValueType.BOOLEAN;
        }
        return switch (type) {
            case BOOLEAN -> BooleanNode.FALSE;
            case STRING -> new StringNode("");
            case NUMBER -> new IntNode(0);
            case JSON -> MAPPER.createObjectNode();
        };
    }

    public static void validate(FlagValueType type, JsonNode value) {
        if (type == null) {
            throw new IllegalArgumentException("FlagValueType must not be null");
        }
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException("Value must not be null for type " + type);
        }

        switch (type) {
            case BOOLEAN -> {
                if (!value.isBoolean()) {
                    throw new IllegalArgumentException("Expected BOOLEAN value, but got: " + value.getNodeType());
                }
            }
            case STRING -> {
                if (!value.isTextual()) {
                    throw new IllegalArgumentException("Expected STRING value, but got: " + value.getNodeType());
                }
            }
            case NUMBER -> {
                if (!value.isNumber()) {
                    throw new IllegalArgumentException("Expected NUMBER value, but got: " + value.getNodeType());
                }
            }
            case JSON -> {
                if (!value.isObject() && !value.isArray()) {
                    throw new IllegalArgumentException("Expected JSON Object or Array for JSON type, but got: " + value.getNodeType());
                }
            }
        }
    }

    public static JsonMapper getMapper() {
        return MAPPER;
    }
}

