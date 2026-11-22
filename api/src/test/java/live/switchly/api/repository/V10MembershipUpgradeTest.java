package live.switchly.api.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class V10MembershipUpgradeTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Test void preservesMembershipsWithoutGrantingEmployeesAnyExistingProject() {
        var source = new PGSimpleDataSource();
        source.setURL(postgres.getJdbcUrl());
        source.setUser(postgres.getUsername());
        source.setPassword(postgres.getPassword());
        Flyway.configure().dataSource(source).target("9").load().migrate();
        var db=JdbcClient.create(source);
        UUID org=db.sql("INSERT INTO organizations(name) VALUES ('Studio') RETURNING id").query(UUID.class).single();
        UUID other=db.sql("INSERT INTO organizations(name) VALUES ('Other') RETURNING id").query(UUID.class).single();
        UUID foreign=db.sql("INSERT INTO projects(organization_id,name) VALUES (?,'App') RETURNING id").param(other).query(UUID.class).single();
        for (String role : new String[]{"OWNER","ADMIN","DEVELOPER","VIEWER"}) {
            UUID user=db.sql("INSERT INTO users(full_name,email,password_hash) VALUES (?,?, 'unused') RETURNING id")
                    .params(role,role+"@test.local").query(UUID.class).single();
            db.sql("INSERT INTO organization_memberships(organization_id,user_id,role) VALUES (?,?,?)").params(org,user,role).update();
            db.sql("INSERT INTO refresh_tokens(user_id,token_hash,expires_at) VALUES (?,?,now()+interval '1 day')").params(user,role).update();
        }
        Flyway.configure().dataSource(source).load().migrate();
        for (String old : new String[]{"OWNER","ADMIN","DEVELOPER","VIEWER"}) {
            assertThat(db.sql("SELECT role FROM organization_memberships m JOIN users u ON u.id=m.user_id WHERE u.full_name=? AND project_id IS NULL")
                    .param(old).query(String.class).single()).isEqualTo(old.equals("OWNER") || old.equals("ADMIN") ? "ADMIN" : "EMPLOYEE");
        }
        assertThat(db.sql("SELECT count(*) FROM refresh_tokens WHERE NOT revoked").query(Integer.class).single()).isZero();
        assertThatThrownBy(() -> db.sql("UPDATE organization_memberships SET project_id=? WHERE role='EMPLOYEE'").param(foreign).update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
