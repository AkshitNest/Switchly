package live.switchly.api.repository;

import live.switchly.api.exception.NotFoundException;
import live.switchly.api.model.SdkKey;
import live.switchly.api.security.SdkPrincipal;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.util.*;

@Repository
public class SdkKeyRepository {
    private static final String METADATA = """
            SELECT k.id, k.name, k.created_at, k.revoked_at, ARRAY(
                SELECT e.key FROM sdk_key_environments a JOIN environments e ON e.id=a.environment_id
                WHERE a.key_id=k.id AND a.project_id=k.project_id ORDER BY e.key
            ) AS environments FROM sdk_keys k WHERE k.project_id=?
            """;
    private static final RowMapper<SdkKey> ROW = (rs, i) -> {
        Timestamp revoked = rs.getTimestamp("revoked_at");
        return new SdkKey(rs.getObject("id", UUID.class), rs.getString("name"),
                List.of((String[])rs.getArray("environments").getArray()), rs.getTimestamp("created_at").toInstant(),
                revoked == null ? null : revoked.toInstant());
    };

    private final JdbcClient db;
    public SdkKeyRepository(JdbcClient db) { this.db = db; }

    public Optional<SdkPrincipal> authenticate(byte[] digest) {
        return db.sql("""
                SELECT k.id,k.project_id,p.key FROM sdk_keys k JOIN projects p ON p.id=k.project_id
                WHERE k.digest=? AND k.revoked_at IS NULL
                """).param(digest).query((rs, i) -> new SdkPrincipal(rs.getObject(1, UUID.class),
                rs.getObject(2, UUID.class), rs.getString(3))).optional();
    }

    public UUID create(UUID project, String name, byte[] digest) {
        return db.sql("INSERT INTO sdk_keys(project_id,name,digest) VALUES (?,?,?) RETURNING id")
                .params(project, name, digest).query(UUID.class).single();
    }

    public int grant(UUID project, UUID id, List<String> environments) {
        return db.sql("""
                INSERT INTO sdk_key_environments(key_id,project_id,environment_id)
                SELECT :id,project_id,id FROM environments WHERE project_id=:project AND key IN (:keys)
                """).param("id", id).param("project", project).param("keys", environments).update();
    }

    public SdkKey get(UUID project, UUID id) {
        return db.sql(METADATA + " AND k.id=?").params(project, id).query(ROW).single();
    }

    public List<SdkKey> list(UUID project) {
        return db.sql(METADATA + " ORDER BY k.created_at,k.id").param(project).query(ROW).list();
    }

    public void revoke(UUID project, UUID key) {
        if (db.sql("UPDATE sdk_keys SET revoked_at=coalesce(revoked_at,now()) WHERE project_id=? AND id=?")
                .params(project, key).update() == 0) throw new NotFoundException("SDK key not found");
    }
}
