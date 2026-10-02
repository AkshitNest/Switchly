package live.switchly.api.repository;

import live.switchly.api.model.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryProjectRepositoryTest {

    private InMemoryProjectRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryProjectRepository();
    }

    @Test
    void saveAndFindById() {
        UUID id = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        Project project = new Project(id, orgId, "Web");
        repository.save(project);

        assertThat(repository.findById(id)).isPresent().get().extracting(Project::getName).isEqualTo("Web");
        assertThat(repository.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void findByOrganizationId() {
        UUID org1 = UUID.randomUUID();
        UUID org2 = UUID.randomUUID();
        repository.save(new Project(UUID.randomUUID(), org1, "A"));
        repository.save(new Project(UUID.randomUUID(), org1, "B"));
        repository.save(new Project(UUID.randomUUID(), org2, "C"));

        assertThat(repository.findByOrganizationId(org1)).extracting(Project::getName).containsExactlyInAnyOrder("A", "B");
    }
}
