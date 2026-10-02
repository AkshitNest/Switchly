package live.switchly.api.repository;

import live.switchly.api.model.Flag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryFlagRepositoryTest {

    private InMemoryFlagRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryFlagRepository();
    }

    @Test
    void saveAndFindById() {
        UUID flagId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        Flag flag = new Flag(flagId, orgId, projectId, "feature-x", "Feature X", "Description X", true);

        repository.save(flag);

        Optional<Flag> found = repository.findById(flagId);
        assertThat(found).isPresent();
        assertThat(found.get().getKey()).isEqualTo("feature-x");
        assertThat(found.get().getDescription()).isEqualTo("Description X");
        assertThat(found.get().isEnabled()).isTrue();
    }

    @Test
    void deleteById_removesFlag() {
        UUID flagId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        Flag flag = new Flag(flagId, orgId, projectId, "feature-x", "Feature X", "Description X", false);

        repository.save(flag);
        assertThat(repository.findById(flagId)).isPresent();

        repository.deleteById(flagId);
        assertThat(repository.findById(flagId)).isEmpty();
    }

    @Test
    void deleteById_nonExistentFlag_doesNotThrow() {
        repository.deleteById(UUID.randomUUID());
    }

    @Test
    void findByProjectId() {
        UUID projectId1 = UUID.randomUUID();
        UUID projectId2 = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();

        Flag flag1 = new Flag(UUID.randomUUID(), orgId, projectId1, "f1", "F 1", false);
        Flag flag2 = new Flag(UUID.randomUUID(), orgId, projectId1, "f2", "F 2", "desc", false);
        Flag flag3 = new Flag(UUID.randomUUID(), orgId, projectId2, "f3", "F 3", false);

        repository.save(flag1);
        repository.save(flag2);
        repository.save(flag3);

        List<Flag> project1Flags = repository.findByProjectId(projectId1);
        assertThat(project1Flags).hasSize(2).extracting(Flag::getKey).containsExactlyInAnyOrder("f1", "f2");
    }

    @Test
    void existsByProjectIdAndKey() {
        UUID projectId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        Flag flag = new Flag(UUID.randomUUID(), orgId, projectId, "f1", "F 1", false);

        repository.save(flag);

        assertThat(repository.existsByProjectIdAndKey(projectId, "f1")).isTrue();
        assertThat(repository.existsByProjectIdAndKey(projectId, "unknown")).isFalse();
    }
}
