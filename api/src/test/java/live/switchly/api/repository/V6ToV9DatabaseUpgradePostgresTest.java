package live.switchly.api.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class V6ToV9DatabaseUpgradePostgresTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Test
    void preservesPopulatedV6AndEnforcesProjectAndEnvironmentOwnership() {
        var source = new PGSimpleDataSource();
        source.setURL(postgres.getJdbcUrl());
        source.setUser(postgres.getUsername());
        source.setPassword(postgres.getPassword());
        Flyway.configure().dataSource(source).target("6").load().migrate();
        var db = JdbcClient.create(source);
        UUID org = db.sql("INSERT INTO organizations(name) VALUES ('Acme') RETURNING id").query(UUID.class).single();
        UUID project = project(db, org);
        UUID other = project(db, org);
        var originalProjects = db.sql("SELECT id,organization_id,name,created_at,updated_at FROM projects ORDER BY id").query().listOfRows();
        UUID env = environment(db, project);
        UUID otherEnv = environment(db, other);
        UUID flag = db.sql("""
                INSERT INTO flags(project_id,key,name,value_type,default_value) VALUES (?,'beta','Beta','JSON','[]') RETURNING id
                """).param(project).query(UUID.class).single();
        UUID config = db.sql("""
                INSERT INTO flag_environment_configs(project_id,flag_id,environment_id,enabled,value)
                VALUES (?,?,?,true,'[false,7]') RETURNING id
                """).params(project,flag,env).query(UUID.class).single();
        var before = db.sql("SELECT id,project_id,flag_id,environment_id,enabled,value::text,created_at,updated_at FROM flag_environment_configs")
                .query().singleRow();

        var flyway = Flyway.configure().dataSource(source).target("9").load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(3);
        flyway.validate();
        assertThat(db.sql("SELECT id,organization_id,name,created_at,updated_at FROM projects ORDER BY id").query().listOfRows())
                .isEqualTo(originalProjects);
        assertThat(db.sql("SELECT key FROM projects").query(String.class).list()).hasSize(2).doesNotHaveDuplicates();
        assertThat(db.sql("SELECT id,project_id,flag_id,environment_id,enabled,value::text,created_at,updated_at FROM flag_environment_configs")
                .query().singleRow()).isEqualTo(before);
        assertThat(db.sql("SELECT rollout_percentage IS NULL FROM flag_environment_configs WHERE id=?").param(config)
                .query(Boolean.class).single()).isTrue();
        assertThat(db.sql("SELECT count(*) FROM targeting_rules").query(Integer.class).single()).isZero();
        assertThat(db.sql("SELECT count(*) FROM sdk_keys").query(Integer.class).single()).isZero();

        db.sql("INSERT INTO user_segments(project_id,key,name,conditions) VALUES (?,'pilot','Pilot','{}')").param(other).update();
        assertThatThrownBy(() -> db.sql("""
                INSERT INTO targeting_rules(config_id,project_id,priority,action,segment_key) VALUES (?,?,0,'SERVE','pilot')
                """).params(config,project).update()).isInstanceOf(DataIntegrityViolationException.class);
        UUID sdk = db.sql("INSERT INTO sdk_keys(project_id,name,digest) VALUES (?,'Server',?) RETURNING id")
                .params(project,new byte[32]).query(UUID.class).single();
        assertThatThrownBy(() -> db.sql("INSERT INTO sdk_key_environments(key_id,project_id,environment_id) VALUES (?,?,?)")
                .params(sdk,project,otherEnv).update()).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> db.sql("UPDATE flag_environment_configs SET rollout_percentage=101 WHERE id=?")
                .param(config).update()).isInstanceOf(DataIntegrityViolationException.class);
    }

    private static UUID project(JdbcClient db, UUID org) {
        return db.sql("INSERT INTO projects(organization_id,name) VALUES (?,'Identical name') RETURNING id")
                .param(org).query(UUID.class).single();
    }

    private static UUID environment(JdbcClient db, UUID project) {
        return db.sql("INSERT INTO environments(project_id,key,name) VALUES (?,'production','Prod') RETURNING id")
                .param(project).query(UUID.class).single();
    }
}
