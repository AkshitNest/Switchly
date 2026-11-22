package live.switchly.api.repository;

import live.switchly.api.model.Organization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryOrganizationRepositoryTest {

    private InMemoryOrganizationRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryOrganizationRepository();
    }

    @Test
    void saveAndFindById() {
        UUID id = UUID.randomUUID();
        Organization org = new Organization(id, "Acme");
        repository.save(org);

        assertThat(repository.findById(id)).isPresent().get().extracting(Organization::getName).isEqualTo("Acme");
        assertThat(repository.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void findAll() {
        repository.save(new Organization(UUID.randomUUID(), "A"));
        repository.save(new Organization(UUID.randomUUID(), "B"));

        assertThat(repository.findAll()).hasSize(2).extracting(Organization::getName).containsExactlyInAnyOrder("A", "B");
    }
}
