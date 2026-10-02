package live.switchly.api.service;

import live.switchly.api.exception.NotFoundException;
import live.switchly.api.model.Organization;
import live.switchly.api.repository.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @InjectMocks
    private OrganizationService organizationService;

    @Test
    void create_savesOrganization() {
        when(organizationRepository.save(any(Organization.class))).thenAnswer(inv -> inv.getArgument(0));

        Organization org = organizationService.create("Acme");

        assertThat(org.getId()).isNotNull();
        assertThat(org.getName()).isEqualTo("Acme");
        verify(organizationRepository).save(any(Organization.class));
    }

    @Test
    void getById_existing_returnsOrganization() {
        UUID id = UUID.randomUUID();
        Organization org = new Organization(id, "Acme");
        when(organizationRepository.findById(id)).thenReturn(Optional.of(org));

        assertThat(organizationService.getById(id)).isEqualTo(org);
    }

    @Test
    void getById_missing_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(organizationRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> organizationService.getById(id))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Organization " + id + " not found");
    }

    @Test
    void getAll_returnsList() {
        List<Organization> orgs = List.of(new Organization(UUID.randomUUID(), "A"));
        when(organizationRepository.findAll()).thenReturn(orgs);

        assertThat(organizationService.getAll()).isEqualTo(orgs);
    }
}
