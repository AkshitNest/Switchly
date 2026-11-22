package live.switchly.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "flag_environment_configs")
public class FlagEnvironmentConfigEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flag_id", nullable = false)
    private FlagEntity flag;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "environment_id", nullable = false)
    private EnvironmentEntity environment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private ProjectEntity project;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "value", columnDefinition = "jsonb", nullable = false)
    private String value;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    private Instant updatedAt;

    protected FlagEnvironmentConfigEntity() {
        // JPA required default constructor
    }

    public FlagEnvironmentConfigEntity(UUID id, FlagEntity flag, EnvironmentEntity environment,
                                       ProjectEntity project, boolean enabled, String value) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.flag = Objects.requireNonNull(flag, "flag must not be null");
        this.environment = Objects.requireNonNull(environment, "environment must not be null");
        this.project = Objects.requireNonNull(project, "project must not be null");
        this.enabled = enabled;
        this.value = Objects.requireNonNull(value, "value must not be null");
    }

    public UUID getId() {
        return id;
    }

    public FlagEntity getFlag() {
        return flag;
    }

    public void setFlag(FlagEntity flag) {
        this.flag = flag;
    }

    public EnvironmentEntity getEnvironment() {
        return environment;
    }

    public void setEnvironment(EnvironmentEntity environment) {
        this.environment = environment;
    }

    public ProjectEntity getProject() {
        return project;
    }

    public void setProject(ProjectEntity project) {
        this.project = project;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
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
        if (!(o instanceof FlagEnvironmentConfigEntity that)) return false;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
