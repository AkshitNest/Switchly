package live.switchly.api.model;

import tools.jackson.databind.node.BooleanNode;
import tools.jackson.databind.node.IntNode;
import tools.jackson.databind.node.StringNode;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlagTest {

    @Test
    void flagConstructor_defaultsToBooleanAndFalse() {
        UUID id = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        Flag flag = new Flag(id, orgId, projectId, "flag-key", "Flag Name", "Custom Description");

        assertThat(flag.getId()).isEqualTo(id);
        assertThat(flag.getOrganizationId()).isEqualTo(orgId);
        assertThat(flag.getProjectId()).isEqualTo(projectId);
        assertThat(flag.getKey()).isEqualTo("flag-key");
        assertThat(flag.getName()).isEqualTo("Flag Name");
        assertThat(flag.getDescription()).isEqualTo("Custom Description");
        assertThat(flag.getValueType()).isEqualTo(FlagValueType.BOOLEAN);
        assertThat(flag.getDefaultValue()).isEqualTo(BooleanNode.FALSE);
    }

    @Test
    void flagConstructor_typedValues() {
        UUID id = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        Flag stringFlag = new Flag(id, orgId, projectId, "theme", "Theme", "Theme name",
                FlagValueType.STRING, new StringNode("light"));
        assertThat(stringFlag.getValueType()).isEqualTo(FlagValueType.STRING);
        assertThat(stringFlag.getDefaultValue().asText()).isEqualTo("light");

        Flag numFlag = new Flag(UUID.randomUUID(), orgId, projectId, "rate-limit", "Rate Limit", null,
                FlagValueType.NUMBER, new IntNode(100));
        assertThat(numFlag.getValueType()).isEqualTo(FlagValueType.NUMBER);
        assertThat(numFlag.getDefaultValue().asInt()).isEqualTo(100);
    }

    @Test
    void flagConstructor_rejectsIncompatibleDefaultValue() {
        UUID id = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        assertThatThrownBy(() -> new Flag(id, orgId, projectId, "invalid", "Invalid", null,
                FlagValueType.BOOLEAN, new StringNode("not-a-bool")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateDefaultValue_validatesType() {
        Flag flag = new Flag(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "flag-key", "Name");
        flag.setDefaultValue(BooleanNode.TRUE);
        assertThat(flag.getDefaultValue()).isEqualTo(BooleanNode.TRUE);

        assertThatThrownBy(() -> flag.setDefaultValue(new IntNode(42)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
