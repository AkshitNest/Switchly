package live.switchly.api.controller;

import live.switchly.api.model.*;
import live.switchly.api.repository.UserRepository;
import live.switchly.api.security.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
@Testcontainers
class EvaluationIntegrationTest extends ScopedSessionSupport {
    @Container @ServiceConnection static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
            .withCommand("postgres", "-c", "shared_preload_libraries=pg_stat_statements");
    @Autowired MockMvc mvc;
    @Autowired JdbcClient db;
    @Autowired JwtService jwt;
    @Autowired UserRepository users;
    @Autowired RateLimitProperties limits;
    @Autowired RateLimiterService limiter;

    private static final String API = "/api/v1";
    private static final String EVALUATE = API + "/evaluate";
    private static final String GROUP = """
            {"match":"AND","conditions":[{"attribute":"country","operator":"EQUALS","value":"IN"}]}
            """;
    private static final String REQUEST = """
            {"projectKey":"payments","environmentKey":"production","flagKey":"beta","context":{"userId":"u","country":"IN"}}
            """;
    private String owner, project, org, env, config, keys, segments, sdk, keyId;

    @BeforeEach
    void setup() throws Exception {
        var user = user();
        owner = "Bearer " + workspaceToken(user);
        org = call(post(API + "/orgs"), owner, "{\"name\":\"Acme\"}", 201).get("id").stringValue();
        owner = "Bearer " + workspaceToken(user, org);
        project = call(post(API + "/orgs/" + org + "/projects"), owner, "{\"name\":\"App\",\"key\":\"payments\"}", 201).get("id").stringValue();
        env = db.sql("SELECT id FROM environments WHERE project_id=? AND key='production'").param(UUID.fromString(project)).query(String.class).single();
        String flag = flag("beta");
        config = API + "/environments/" + env + "/flags/" + flag;
        keys = API + "/projects/" + project + "/sdk-keys";
        segments = API + "/projects/" + project + "/segments";
        var issued = call(post(keys), owner, "{\"name\":\"Backend\",\"environmentKeys\":[\"production\"]}", 201);
        sdk = "SDK " + issued.get("secret").stringValue();
        keyId = issued.at("/key/id").stringValue();
        call(put(config + "/state"), owner, "{\"enabled\":true}", 200);
        call(put(config + "/value"), owner, "{\"value\":true}", 200);
        limiter.reset();
    }

    private User user() {
        var id = UUID.randomUUID();
        return users.save(new User(id, "Test", id + "@example.org", "unused", true, true));
    }

    private String flag(String key) throws Exception {
        return call(post(API + "/projects/" + project + "/flags"), owner,
                "{\"key\":\"" + key + "\",\"name\":\"Feature\"}", 201).get("id").stringValue();
    }

    private JsonNode call(MockHttpServletRequestBuilder request, String authorization, String body, int expected) throws Exception {
        if (authorization != null) request.header("Authorization", authorization);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(body);
        var response = mvc.perform(request).andExpect(status().is(expected)).andReturn().getResponse();
        return FlagValueValidator.getMapper().readTree(response.getContentAsByteArray());
    }

    @Test
    void orderedRulesSegmentLifecycleAndRollout() throws Exception {
        String segment = "{\"name\":\"Indian users\",\"description\":\"Pilot\",\"conditions\":" + GROUP + "}";
        String create = "{\"key\":\"pilot\"," + segment.substring(1);
        var saved = call(post(segments), owner, create, 201);
        assertThat(saved.get("projectId").stringValue()).isEqualTo(project);
        assertThat(saved.hasNonNull("createdAt") && saved.hasNonNull("updatedAt")).isTrue();
        call(post(segments), owner, create, 409);
        assertThat(call(get(segments), owner, null, 200).size()).isEqualTo(1);
        assertThat(call(get(segments + "/pilot"), owner, null, 200)).isEqualTo(saved);
        assertThat(call(post(segments + "/pilot/evaluate"), owner, "{\"context\":{\"country\":\"IN\"}}", 200)
                .get("member").booleanValue()).isTrue();
        assertThat(call(post(segments + "/pilot/evaluate"), owner, "{}", 200).get("member").booleanValue()).isFalse();
        String rules = "[{\"action\":\"SERVE\",\"segmentKey\":\"pilot\"},{\"action\":\"DEFAULT\",\"conditions\":" + GROUP + "}]";
        var written = call(put(config + "/targeting"), owner, rules, 200);
        assertThat(call(get(config + "/targeting"), owner, null, 200)).isEqualTo(written);
        call(delete(segments + "/pilot"), owner, null, 409);
        call(put(config + "/targeting"), owner, "[{\"action\":\"SERVE\",\"segmentKey\":\"absent\"}]", 409);
        assertThat(call(get(config + "/targeting"), owner, null, 200)).isEqualTo(written);
        assertResult(REQUEST, true, "TARGETING_MATCH");
        assertResult(REQUEST.replace("\"IN\"", "\"CA\""), false, "TARGETING_NO_MATCH");
        assertThat(call(get(config + "/rollout"), owner, null, 200).get("percentage").isNull()).isTrue();
        call(put(config + "/rollout"), owner, "{\"percentage\":100}", 200);
        assertResult(REQUEST, true, "ROLLOUT_INCLUDED");
        assertResult(REQUEST.replace("\"userId\":\"u\",",""), false, "ROLLOUT_MISSING_IDENTITY");
        call(put(config + "/rollout"), owner, "{\"percentage\":0}", 200);
        assertResult(REQUEST, false, "ROLLOUT_EXCLUDED");
        call(put(segments + "/pilot"), owner, segment.replace("\"IN\"", "\"CA\""), 200);
        assertResult(REQUEST, false, "TARGETING_DEFAULT");
        call(put(config + "/targeting"), owner, "[]", 200);
        call(put(config + "/rollout"), owner, "{\"percentage\":null}", 200);
        call(delete(segments + "/pilot"), owner, null, 204);
        call(get(segments + "/pilot"), owner, null, 404);
        assertResult(REQUEST, true, "FLAG_ENABLED");
        call(put(config + "/state"), owner, "{\"enabled\":false}", 200);
        assertResult(REQUEST, false, "FLAG_DISABLED_DEFAULT");
    }

    private void assertResult(String request, boolean expected, String reason) throws Exception {
        JsonNode result = call(post(EVALUATE), sdk, request, 200);
        assertThat(result.get("reason").stringValue()).isEqualTo(reason);
        assertThat(result.get("value")).isEqualTo(FlagValueValidator.getMapper().readTree(Boolean.toString(expected)));
        assertThat(result.propertyNames()).containsExactlyInAnyOrder("flagKey","environmentKey","enabled","valueType","value","reason","evaluatedAt");
    }

    @Test
    void scopedCredentialsAndRevocation() throws Exception {
        var stored = db.sql("SELECT digest FROM sdk_keys WHERE id=?").param(UUID.fromString(keyId)).query(byte[].class).single();
        assertThat(stored).containsExactly(SdkSecrets.digest(sdk.substring(4)));
        var listed = call(get(keys), owner, null, 200);
        assertThat(listed.toString()).doesNotContain(sdk.substring(4), "digest", "secret");
        assertThat(listed.at("/0/environmentKeys/0").stringValue()).isEqualTo("production");
        for (String credential : new String[]{null, owner, "SDK invalid", "SDK " + SdkSecrets.generate()}) {
            call(post(EVALUATE), credential, REQUEST, 401);
        }
        call(get(segments), sdk, null, 401);
        call(post(EVALUATE), sdk, REQUEST.replace("production","staging"), 403);
        call(post(EVALUATE), sdk, REQUEST.replace("production","unknown"), 403);
        call(post(EVALUATE), sdk, REQUEST.replace("payments","other"), 403);
        call(post(keys), owner, "{\"name\":\"bad\",\"environmentKeys\":[\"production\",\"missing\"]}", 400);
        assertThat(call(get(keys), owner, null, 200).size()).isEqualTo(1);
        call(delete(keys + "/" + keyId), owner, null, 204);
        call(delete(keys + "/" + keyId), owner, null, 204);
        assertThat(call(get(keys), owner, null, 200).at("/0/revokedAt").isString()).isTrue();
        call(post(EVALUATE), sdk, REQUEST, 401);
    }

    @Test
    void isolatesOrgsWithIdenticalProjectKeysAndEnforcesRoles() throws Exception {
        var otherUser = user();
        var otherOwner = "Bearer " + workspaceToken(otherUser);
        var otherOrg = call(post(API + "/orgs"), otherOwner, "{\"name\":\"Other\"}", 201).get("id").stringValue();
        otherOwner = "Bearer " + workspaceToken(otherUser, otherOrg);
        var other = call(post(API + "/orgs/" + otherOrg + "/projects"), otherOwner, "{\"name\":\"Other\",\"key\":\"payments\"}", 201).get("id").stringValue();
        String otherProject = API + "/projects/" + other;
        call(post(otherProject + "/segments"), otherOwner, "{\"key\":\"foreign\",\"name\":\"Foreign\",\"conditions\":" + GROUP + "}", 201);
        call(get(otherProject + "/segments/foreign"), owner, null, 403);
        call(put(config + "/targeting"), owner, "[{\"action\":\"SERVE\",\"segmentKey\":\"foreign\"}]", 409);
        var otherSdk = call(post(otherProject + "/sdk-keys"), otherOwner, "{\"name\":\"Server\",\"environmentKeys\":[\"production\"]}", 201);
        call(post(EVALUATE), "SDK " + otherSdk.get("secret").stringValue(), REQUEST, 404);
        call(delete(otherProject + "/sdk-keys/" + keyId), otherOwner, null, 404);
        var viewer = user();
        db.sql("INSERT INTO organization_memberships(organization_id,user_id,role) VALUES (?,?,'EMPLOYEE')")
                .params(UUID.fromString(org), viewer.getId()).update();
        String reader = "Bearer " + workspaceToken(viewer);
        call(get(segments), reader, null, 403);
        call(put(config + "/targeting"), reader, "[]", 403);
        call(put(config + "/rollout"), reader, "{\"percentage\":100}", 403);
        call(delete(segments + "/foreign"), reader, null, 403);
        call(get(keys), reader, null, 403);
        call(delete(keys + "/" + keyId), reader, null, 403);
        assertResult(REQUEST, true, "FLAG_ENABLED");
    }

    @Test
    void batchIsOrderedAtomicAndReflectsConfigChanges() throws Exception {
        String second = flag("second");
        String batch = REQUEST.replace("\"flagKey\":\"beta\"", "\"flagKeys\":[\"second\",\"beta\"]");
        var values = call(post(EVALUATE + "/batch"), sdk, batch, 200);
        assertThat(values.at("/0/flagKey").stringValue()).isEqualTo("second");
        assertThat(values.at("/0/value").booleanValue()).isFalse();
        assertThat(values.at("/1/value").booleanValue()).isTrue();
        assertThat(values.at("/0/evaluatedAt")).isEqualTo(values.at("/1/evaluatedAt"));
        call(post(EVALUATE + "/batch"), sdk, batch.replace("second", "beta"), 400);
        call(post(EVALUATE + "/batch"), sdk, batch.replace("second", "unknown"), 404);
        db.sql("DELETE FROM flag_environment_configs WHERE environment_id=? AND flag_id=?")
                .params(UUID.fromString(env), UUID.fromString(second)).update();
        call(post(EVALUATE + "/batch"), sdk, batch, 404);
        call(post(EVALUATE), sdk, REQUEST.replace("beta","second"), 404);
    }

    @Test
    void typedValuesAndExactDecimalComparisons() throws Exception {
        String precise = "0.12345678901234567890123456789";
        var created = call(post(API + "/projects/" + project + "/flags"), owner,
                "{\"key\":\"rate\",\"name\":\"Rate\",\"valueType\":\"NUMBER\",\"defaultValue\":" + precise + "}", 201);
        String rate = API + "/environments/" + env + "/flags/" + created.get("id").stringValue();
        call(put(rate + "/value"), owner, "{\"value\":2}", 200);
        call(put(rate + "/state"), owner, "{\"enabled\":true}", 200);
        String rule = "[{\"action\":\"SERVE\",\"conditions\":{\"match\":\"AND\",\"conditions\":[{\"attribute\":\"custom.rate\",\"operator\":\"EQUALS\",\"value\":" + precise + "}]}}]";
        call(put(rate + "/targeting"), owner, rule, 200);
        String request = REQUEST.replace("beta","rate").replace("\"country\":\"IN\"", "\"custom\":{\"rate\":" + precise + "}");
        assertThat(call(post(EVALUATE), sdk, request, 200).get("value").intValue()).isEqualTo(2);
        assertThat(call(post(EVALUATE), sdk, request.replace(precise, precise + "1"), 200).get("value").decimalValue())
                .isEqualByComparingTo(precise);
    }

    @Test
    void fullBatchUsesTheSameFourDataQueriesAsOneFlag() throws Exception {
        db.sql("CREATE EXTENSION IF NOT EXISTS pg_stat_statements").update();
        db.sql("""
                WITH added AS (
                    INSERT INTO flags(project_id,key,name)
                    SELECT ?, 'batch-' || i, 'Batch' FROM generate_series(1,99) AS i RETURNING id,project_id
                )
                INSERT INTO flag_environment_configs(project_id,flag_id,environment_id,enabled,value)
                SELECT project_id,id,?,true,'true' FROM added
                """).params(UUID.fromString(project),UUID.fromString(env)).update();
        db.sql("INSERT INTO user_segments(project_id,key,name,conditions) VALUES (?,'pilot','Pilot',CAST(? AS jsonb))")
                .params(UUID.fromString(project), GROUP).update();
        db.sql("""
                INSERT INTO targeting_rules(config_id,project_id,priority,action,segment_key)
                SELECT id,project_id,0,'SERVE','pilot' FROM flag_environment_configs WHERE environment_id=?
                """).param(UUID.fromString(env)).update();
        var mapper = FlagValueValidator.getMapper();
        var batch = (tools.jackson.databind.node.ObjectNode) mapper.readTree(REQUEST);
        batch.remove("flagKey");
        var keys = batch.putArray("flagKeys");
        keys.add("beta");
        for (int i = 1; i <= 99; i++) keys.add("batch-" + i);

        resetQueryCounts();
        assertResult(REQUEST, true, "TARGETING_MATCH");
        assertThat(dataQueryCount()).isEqualTo(4);
        resetQueryCounts();
        var results = call(post(EVALUATE + "/batch"), sdk, batch.toString(), 200);
        assertThat(results.size()).isEqualTo(100);
        assertThat(results.valueStream()).allMatch(r -> r.get("reason").stringValue().equals("TARGETING_MATCH"));
        assertThat(dataQueryCount()).isEqualTo(4);
        keys.add("one-too-many");
        call(post(EVALUATE + "/batch"), sdk, batch.toString(), 400);
    }

    private void resetQueryCounts() {
        db.sql("SELECT pg_stat_statements_reset()").query().singleRow();
    }

    private long dataQueryCount() {
        return db.sql("""
                SELECT coalesce(sum(calls), 0) FROM pg_stat_statements
                WHERE dbid=(SELECT oid FROM pg_database WHERE datname=current_database())
                  AND query ILIKE 'SELECT %' AND query NOT ILIKE '%pg_stat_statements%'
                """).query(Long.class).single();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"percentage\":101}", "{\"percentage\":-1}", "{\"percentage\":1.2}",
            "{\"percentage\":\"10\"}", "{\"percentage\":2147483648}", "{\"percentage\":100,\"extra\":1}"})
    void rejectsInvalidRollout(String body) throws Exception {
        call(put(config + "/rollout"), owner, body, 400);
    }

    @Test
    void handlesBadContextsMalformedInputAndStoredCorruption() throws Exception {
        call(post(EVALUATE), sdk, "{", 400);
        call(post(EVALUATE), sdk, REQUEST.replace("\"u\"", "123"), 400);
        call(post(EVALUATE), sdk, REQUEST.replace("\"country\"", "\"unknown\""), 400);
        call(post(EVALUATE), sdk, REQUEST.replace("\"u\"", "\"\""), 400);
        call(post(EVALUATE), sdk, REQUEST.replace("\"country\":\"IN\"", "\"custom\":{\"nested\":{}}"), 400);
        db.sql("UPDATE flag_environment_configs SET value='42' WHERE environment_id=?").param(UUID.fromString(env)).update();
        var failure = call(post(EVALUATE), sdk, REQUEST, 500);
        assertThat(failure.toString()).doesNotContain("42", project, sdk.substring(4), "SQLException");
    }

    @Test
    void limitsEvaluationAndBatchTogetherBeforeAuthentication() throws Exception {
        int original = limits.getEvaluationLimit();
        limits.setEvaluationLimit(2);
        try {
            call(post(EVALUATE), null, REQUEST, 401);
            assertResult(REQUEST, true, "FLAG_ENABLED");
            mvc.perform(post(EVALUATE + "/batch")).andExpect(status().isTooManyRequests())
                    .andExpect(header().exists("Retry-After")).andExpect(jsonPath("$.error.code").value("RATE_LIMIT_EXCEEDED"));
        } finally {
            limits.setEvaluationLimit(original);
            limiter.reset();
        }
    }
}
