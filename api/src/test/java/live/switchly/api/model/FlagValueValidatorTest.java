package live.switchly.api.model;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.BooleanNode;
import tools.jackson.databind.node.DoubleNode;
import tools.jackson.databind.node.IntNode;
import tools.jackson.databind.node.NullNode;
import tools.jackson.databind.node.StringNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlagValueValidatorTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void defaultValues() {
        assertThat(FlagValueValidator.defaultFor(FlagValueType.BOOLEAN)).isEqualTo(BooleanNode.FALSE);
        assertThat(FlagValueValidator.defaultFor(FlagValueType.STRING).asText()).isEqualTo("");
        assertThat(FlagValueValidator.defaultFor(FlagValueType.NUMBER).asInt()).isEqualTo(0);
        assertThat(FlagValueValidator.defaultFor(FlagValueType.JSON).toString()).isEqualTo("{}");
    }

    @Test
    void validateBoolean() {
        assertThatCode(() -> FlagValueValidator.validate(FlagValueType.BOOLEAN, BooleanNode.TRUE)).doesNotThrowAnyException();
        assertThatCode(() -> FlagValueValidator.validate(FlagValueType.BOOLEAN, BooleanNode.FALSE)).doesNotThrowAnyException();

        assertThatThrownBy(() -> FlagValueValidator.validate(FlagValueType.BOOLEAN, new StringNode("true")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FlagValueValidator.validate(FlagValueType.BOOLEAN, new IntNode(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validateString() {
        assertThatCode(() -> FlagValueValidator.validate(FlagValueType.STRING, new StringNode("hello"))).doesNotThrowAnyException();

        assertThatThrownBy(() -> FlagValueValidator.validate(FlagValueType.STRING, BooleanNode.TRUE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FlagValueValidator.validate(FlagValueType.STRING, new IntNode(123)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validateNumber() {
        assertThatCode(() -> FlagValueValidator.validate(FlagValueType.NUMBER, new IntNode(42))).doesNotThrowAnyException();
        assertThatCode(() -> FlagValueValidator.validate(FlagValueType.NUMBER, new DoubleNode(3.14))).doesNotThrowAnyException();

        assertThatThrownBy(() -> FlagValueValidator.validate(FlagValueType.NUMBER, new StringNode("42")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FlagValueValidator.validate(FlagValueType.NUMBER, BooleanNode.FALSE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validateJson() throws Exception {
        assertThatCode(() -> FlagValueValidator.validate(FlagValueType.JSON, mapper.readTree("{\"key\": \"value\"}"))).doesNotThrowAnyException();
        assertThatCode(() -> FlagValueValidator.validate(FlagValueType.JSON, mapper.readTree("[1, 2, 3]"))).doesNotThrowAnyException();

        assertThatThrownBy(() -> FlagValueValidator.validate(FlagValueType.JSON, new StringNode("plain text")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FlagValueValidator.validate(FlagValueType.JSON, new IntNode(10)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validateNull() {
        assertThatThrownBy(() -> FlagValueValidator.validate(FlagValueType.BOOLEAN, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FlagValueValidator.validate(FlagValueType.STRING, NullNode.getInstance()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

