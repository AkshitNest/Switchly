package live.switchly.api.repository;

import live.switchly.api.model.Environment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryEnvironmentRepositoryTest {

    private InMemoryEnvironmentRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryEnvironmentRepository();
    }

    @Test
    void saveAndFindById() {
        UUID id = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Environment env = new Environment(id, projectId, "dev", "Dev", "desc", true);

        repository.save(env);

        Optional<Environment> found = repository.findById(id);
        assertThat(found).isPresent();
        assertThat(found.get().getKey()).isEqualTo("dev");
        assertThat(found.get().getName()).isEqualTo("Dev");
        assertThat(found.get().isDefault()).isTrue();
    }

    @Test
    void findByProjectId_returnsOnlyProjectEnvironments() {
        UUID project1 = UUID.randomUUID();
        UUID project2 = UUID.randomUUID();

        Environment env1 = new Environment(UUID.randomUUID(), project1, "dev", "Dev", null, true);
        Environment env2 = new Environment(UUID.randomUUID(), project1, "prod", "Prod", null, true);
        Environment env3 = new Environment(UUID.randomUUID(), project2, "dev", "Dev", null, true);

        repository.save(env1);
        repository.save(env2);
        repository.save(env3);

        List<Environment> project1Envs = repository.findByProjectId(project1);
        assertThat(project1Envs).hasSize(2)
                .extracting(Environment::getKey)
                .containsExactlyInAnyOrder("dev", "prod");
    }

    @Test
    void findByProjectIdAndKey_and_existsByProjectIdAndKey() {
        UUID projectId = UUID.randomUUID();
        Environment env = new Environment(UUID.randomUUID(), projectId, "staging", "Staging", null, true);
        repository.save(env);

        assertThat(repository.existsByProjectIdAndKey(projectId, "staging")).isTrue();
        assertThat(repository.existsByProjectIdAndKey(projectId, "non-existent")).isFalse();

        Optional<Environment> found = repository.findByProjectIdAndKey(projectId, "staging");
        assertThat(found).isPresent();
        assertThat(found.get().getKey()).isEqualTo("staging");

        assertThat(repository.findByProjectIdAndKey(projectId, "non-existent")).isEmpty();
    }

    @Test
    void deleteById() {
        UUID id = UUID.randomUUID();
        Environment env = new Environment(id, UUID.randomUUID(), "test", "Test", null, false);
        repository.save(env);

        assertThat(repository.findById(id)).isPresent();
        repository.deleteById(id);
        assertThat(repository.findById(id)).isEmpty();
    }
}

