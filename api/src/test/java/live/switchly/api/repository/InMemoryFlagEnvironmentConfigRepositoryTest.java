package live.switchly.api.repository;

import tools.jackson.databind.node.BooleanNode;
import live.switchly.api.model.FlagEnvironmentConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryFlagEnvironmentConfigRepositoryTest {

    private InMemoryFlagEnvironmentConfigRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryFlagEnvironmentConfigRepository();
    }

    @Test
    void saveAndFind() {
        UUID id = UUID.randomUUID();
        UUID flagId = UUID.randomUUID();
        UUID envId = UUID.randomUUID();
        UUID projId = UUID.randomUUID();

        FlagEnvironmentConfig config = new FlagEnvironmentConfig(id, flagId, envId, projId, true, BooleanNode.TRUE);
        repository.save(config);

        Optional<FlagEnvironmentConfig> found = repository.findById(id);
        assertThat(found).isPresent();
        assertThat(found.get().isEnabled()).isTrue();
        assertThat(found.get().getValue()).isEqualTo(BooleanNode.TRUE);

        Optional<FlagEnvironmentConfig> byFlagAndEnv = repository.findByFlagIdAndEnvironmentId(flagId, envId);
        assertThat(byFlagAndEnv).isPresent();
        assertThat(byFlagAndEnv.get().getId()).isEqualTo(id);
    }

    @Test
    void findByEnvironmentId_and_findByProjectId() {
        UUID projId = UUID.randomUUID();
        UUID env1 = UUID.randomUUID();
        UUID env2 = UUID.randomUUID();
        UUID flag1 = UUID.randomUUID();

        FlagEnvironmentConfig c1 = new FlagEnvironmentConfig(UUID.randomUUID(), flag1, env1, projId, false, BooleanNode.FALSE);
        FlagEnvironmentConfig c2 = new FlagEnvironmentConfig(UUID.randomUUID(), flag1, env2, projId, true, BooleanNode.TRUE);

        repository.save(c1);
        repository.save(c2);

        List<FlagEnvironmentConfig> env1Configs = repository.findByEnvironmentId(env1);
        assertThat(env1Configs).containsExactly(c1);

        List<FlagEnvironmentConfig> projectConfigs = repository.findByProjectId(projId);
        assertThat(projectConfigs).containsExactlyInAnyOrder(c1, c2);
    }

    @Test
    void deleteByFlagId() {
        UUID flagId = UUID.randomUUID();
        FlagEnvironmentConfig c1 = new FlagEnvironmentConfig(UUID.randomUUID(), flagId, UUID.randomUUID(), UUID.randomUUID(), false, BooleanNode.FALSE);
        repository.save(c1);

        assertThat(repository.existsByFlagIdAndEnvironmentId(flagId, c1.getEnvironmentId())).isTrue();
        repository.deleteByFlagId(flagId);
        assertThat(repository.existsByFlagIdAndEnvironmentId(flagId, c1.getEnvironmentId())).isFalse();
    }
}

