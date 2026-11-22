package live.switchly.api.repository;

import live.switchly.api.exception.ForbiddenException;
import live.switchly.api.model.*;
import live.switchly.api.security.SdkPrincipal;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public class EvaluationRepository {
    private final JdbcClient db;
    public EvaluationRepository(JdbcClient db) { this.db = db; }

    public Environment permittedEnvironment(SdkPrincipal principal, String key) {
        return db.sql("""
                SELECT e.id,e.key,e.name FROM sdk_key_environments a
                JOIN sdk_keys k ON k.id=a.key_id AND k.project_id=a.project_id
                JOIN environments e ON e.id=a.environment_id AND e.project_id=a.project_id
                WHERE a.key_id=? AND a.project_id=? AND k.revoked_at IS NULL AND e.key=?
                """).params(principal.keyId(), principal.projectId(), key).query((rs, i) ->
                new Environment(rs.getObject("id", UUID.class), principal.projectId(), rs.getString("key"),
                        rs.getString("name"), null, false)).optional()
                .orElseThrow(() -> new ForbiddenException("SDK key does not grant this scope"));
    }

    public record LoadedFlag(Flag flag, FlagEnvironmentConfig config, Integer percentage) {}

    public List<LoadedFlag> load(Environment env, List<String> keys) {
        return db.sql("""
                SELECT f.id AS flag_id, f.key, f.name, f.value_type, f.default_value,
                       c.id AS config_id, c.enabled, c.value, c.rollout_percentage
                FROM flags f JOIN flag_environment_configs c ON c.flag_id=f.id AND c.project_id=f.project_id
                WHERE f.project_id=:project AND c.environment_id=:env AND f.key IN (:keys)
                """).param("project", env.getProjectId()).param("env", env.getId()).param("keys", keys).query((rs, i) -> {
            try {
                var mapper = FlagValueValidator.getMapper();
                var flag = new Flag(rs.getObject("flag_id", UUID.class), null, env.getProjectId(), rs.getString("key"),
                        rs.getString("name"), null, FlagValueType.valueOf(rs.getString("value_type")),
                        mapper.readTree(rs.getString("default_value")));
                var config = new FlagEnvironmentConfig(rs.getObject("config_id", UUID.class), flag.getId(), env.getId(),
                        env.getProjectId(), rs.getBoolean("enabled"), mapper.readTree(rs.getString("value")));
                return new LoadedFlag(flag, config, rs.getObject("rollout_percentage", Integer.class));
            } catch (IllegalArgumentException ex) {
                throw new IllegalStateException("Invalid stored flag data", ex);
            }
        }).list();
    }
}
