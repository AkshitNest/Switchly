package live.switchly.api.repository;

import live.switchly.api.evaluation.ConditionGroup;
import live.switchly.api.exception.ConflictException;
import live.switchly.api.exception.NotFoundException;
import live.switchly.api.model.FlagValueValidator;
import live.switchly.api.model.UserSegment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public class SegmentRepository {
    private final JdbcClient db;
    private static final RowMapper<UserSegment> ROW = (rs, i) -> new UserSegment(rs.getObject("id", UUID.class),
            rs.getObject("project_id", UUID.class), rs.getString("key"), rs.getString("name"), rs.getString("description"),
            ConditionGroup.parse(FlagValueValidator.getMapper().readTree(rs.getString("conditions"))),
            rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());

    public SegmentRepository(JdbcClient db) { this.db = db; }

    public List<UserSegment> list(UUID project) {
        return db.sql("SELECT * FROM user_segments WHERE project_id = ? ORDER BY key").param(project).query(ROW).list();
    }

    public UserSegment get(UUID project, String key) {
        return db.sql("SELECT * FROM user_segments WHERE project_id = ? AND key = ?").params(project, key)
                .query(ROW).optional().orElseThrow(SegmentRepository::missing);
    }

    public UserSegment save(UUID project, String key, String name, String description, ConditionGroup conditions, boolean create) {
        String sql = create ? """
                INSERT INTO user_segments(project_id, key, name, description, conditions)
                VALUES (:project,:key,:name,:description,CAST(:group AS jsonb)) RETURNING *
                """ : """
                UPDATE user_segments SET name=:name, description=:description, conditions=CAST(:group AS jsonb)
                WHERE project_id=:project AND key=:key RETURNING *
                """;
        try {
            return db.sql(sql).param("project", project).param("key", key).param("name", name)
                    .param("description", description).param("group", FlagValueValidator.getMapper().writeValueAsString(conditions))
                    .query(ROW).optional().orElseThrow(SegmentRepository::missing);
        } catch (DuplicateKeyException ex) {
            throw new ConflictException("Segment key already exists in this project");
        }
    }

    public void delete(UUID project, String key) {
        try {
            if (db.sql("DELETE FROM user_segments WHERE project_id=? AND key=?").params(project, key).update() == 0) throw missing();
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Remove targeting rules that reference this segment before deletion");
        }
    }

    private static NotFoundException missing() { return new NotFoundException("Segment not found"); }
}
