package live.switchly.api.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FlagTest {

    @Test
    void flagConstructor_withDescription() {
        UUID id = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        Flag flag = new Flag(id, orgId, projectId, "flag-key", "Flag Name", "Custom Description", false);

        assertThat(flag.getId()).isEqualTo(id);
        assertThat(flag.getOrganizationId()).isEqualTo(orgId);
        assertThat(flag.getProjectId()).isEqualTo(projectId);
        assertThat(flag.getKey()).isEqualTo("flag-key");
        assertThat(flag.getName()).isEqualTo("Flag Name");
        assertThat(flag.getDescription()).isEqualTo("Custom Description");
        assertThat(flag.isEnabled()).isFalse();

        flag.setEnabled(true);
        assertThat(flag.isEnabled()).isTrue();
    }

    @Test
    void flagConstructor_withoutDescription_defaultsToNull() {
        UUID id = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        Flag flag = new Flag(id, orgId, projectId, "flag-key", "Flag Name", true);

        assertThat(flag.getDescription()).isNull();
        assertThat(flag.isEnabled()).isTrue();
    }
}
