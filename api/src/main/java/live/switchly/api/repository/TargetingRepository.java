package live.switchly.api.repository;

import live.switchly.api.evaluation.*;
import live.switchly.api.exception.ConflictException;
import live.switchly.api.exception.NotFoundException;
import live.switchly.api.model.FlagEnvironmentConfig;
import live.switchly.api.model.FlagValueValidator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public class TargetingRepository {
    private final JdbcClient db;
    public TargetingRepository(JdbcClient db) { this.db = db; }

    public List<RuleDefinition> get(UUID project, UUID config) {
        return db.sql("""
                SELECT action, conditions, segment_key FROM targeting_rules
                WHERE project_id=? AND config_id=? ORDER BY priority
                """).params(project, config).query((rs, i) -> {
            String json = rs.getString("conditions");
            return new RuleDefinition(TargetingRule.Action.valueOf(rs.getString("action")),
                    json == null ? null : group(json), rs.getString("segment_key"));
        }).list();
    }

    public Map<UUID, List<TargetingRule>> resolve(UUID project, Collection<UUID> configs) {
        Map<UUID, List<TargetingRule>> byConfig = new HashMap<>();
        Map<String, ConditionGroup> segments = new HashMap<>();
        db.sql("""
                SELECT r.config_id, r.action, r.segment_key, coalesce(r.conditions, s.conditions) AS conditions
                FROM targeting_rules r LEFT JOIN user_segments s ON s.project_id=r.project_id AND s.key=r.segment_key
                WHERE r.project_id=:project AND r.config_id IN (:configs) ORDER BY r.config_id,r.priority
                """).param("project", project).param("configs", configs).query(rs -> {
            while (rs.next()) {
                String segment = rs.getString("segment_key");
                String json = rs.getString("conditions");
                var conditions = segment == null ? group(json) : segments.computeIfAbsent(segment, k -> group(json));
                byConfig.computeIfAbsent(rs.getObject("config_id", UUID.class), k -> new ArrayList<>())
                        .add(new TargetingRule(TargetingRule.Action.valueOf(rs.getString("action")), conditions));
            }
            return byConfig;
        });
        return byConfig;
    }

    private static ConditionGroup group(String json) {
        try {
            return ConditionGroup.parse(FlagValueValidator.getMapper().readTree(json));
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Invalid stored targeting conditions", ex);
        }
    }

    public Integer rollout(UUID config) {
        return db.sql("SELECT rollout_percentage FROM flag_environment_configs WHERE id=?").param(config)
                .query((rs, i) -> Optional.ofNullable(rs.getObject(1, Integer.class))).optional()
                .orElseThrow(() -> new NotFoundException("Configuration not found")).orElse(null);
    }

    public void setRollout(UUID config, Integer percentage) {
        if (db.sql("UPDATE flag_environment_configs SET rollout_percentage=? WHERE id=?").params(percentage, config).update() == 0) {
            throw new NotFoundException("Configuration not found");
        }
    }

    public void replace(FlagEnvironmentConfig config, List<RuleDefinition> rules) {
        db.sql("SELECT id FROM flag_environment_configs WHERE id=? AND project_id=? FOR UPDATE")
                .params(config.getId(), config.getProjectId()).query(UUID.class).optional()
                .orElseThrow(() -> new NotFoundException("Configuration not found"));
        db.sql("DELETE FROM targeting_rules WHERE config_id=?").param(config.getId()).update();
        try {
            for (int priority = 0; priority < rules.size(); priority++) {
                RuleDefinition rule = rules.get(priority);
                String json = rule.conditions() == null ? null : FlagValueValidator.getMapper().writeValueAsString(rule.conditions());
                db.sql("""
                        INSERT INTO targeting_rules(config_id,project_id,priority,action,conditions,segment_key)
                        VALUES (?,?,?,?,CAST(? AS jsonb),?)
                        """).params(config.getId(), config.getProjectId(), priority, rule.action().name(), json, rule.segmentKey()).update();
            }
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Referenced segment must exist in the project");
        }
    }
}
