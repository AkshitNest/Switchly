package live.switchly.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "projects")
public class ProjectEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private OrganizationEntity organization;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "key", nullable = false, updatable = false)
    private String key;

    @OneToMany(mappedBy = "project", fetch = FetchType.LAZY)
    private List<FlagEntity> flags = new ArrayList<>();

    @OneToMany(mappedBy = "project", fetch = FetchType.LAZY)
    private List<EnvironmentEntity> environments = new ArrayList<>();

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    private Instant updatedAt;

    protected ProjectEntity() {
        // JPA required default constructor
    }

    public ProjectEntity(UUID id, OrganizationEntity organization, String name) {
        this(id, organization, name, "p-" + UUID.randomUUID());
    }

    public ProjectEntity(UUID id, OrganizationEntity organization, String name, String key) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.organization = Objects.requireNonNull(organization, "organization must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.key = Objects.requireNonNull(key);
    }

    public UUID getId() {
        return id;
    }

    public OrganizationEntity getOrganization() {
        return organization;
    }

    public void setOrganization(OrganizationEntity organization) {
        this.organization = organization;
    }

    public String getName() {
        return name;
    }

    public String getKey() { return key; }

    public void setName(String name) {
        this.name = name;
    }

    public List<FlagEntity> getFlags() {
        return Collections.unmodifiableList(flags);
    }

    public List<EnvironmentEntity> getEnvironments() {
        return Collections.unmodifiableList(environments);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProjectEntity that)) return false;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
