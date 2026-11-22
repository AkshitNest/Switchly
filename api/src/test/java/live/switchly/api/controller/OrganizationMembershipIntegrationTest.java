package live.switchly.api.controller;

import live.switchly.api.model.Organization;
import live.switchly.api.model.OrganizationMembership;
import live.switchly.api.model.Role;
import live.switchly.api.model.User;
import live.switchly.api.repository.OrganizationMembershipRepository;
import live.switchly.api.repository.OrganizationRepository;
import live.switchly.api.repository.UserRepository;
import live.switchly.api.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class OrganizationMembershipIntegrationTest extends ScopedSessionSupport {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private OrganizationMembershipRepository membershipRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired org.springframework.jdbc.core.simple.JdbcClient db;
    @Autowired live.switchly.api.service.UserService users;
    private UUID org, project, otherProject;
    private User admin;
    private String token, path;

    @BeforeEach void setup() {
        admin = users.create("Admin", UUID.randomUUID() + "@example.org", "Password123");
        org = testOrganizations.create("Studio", admin.getId()).getId();
        token = workspaceToken(admin, org.toString());
        project = createProject();
        otherProject = createProject();
        path = "/api/v1/orgs/" + org + "/members";
    }
    private UUID createProject() {
        UUID id = UUID.randomUUID();
        db.sql("INSERT INTO projects(id,organization_id,key,name) VALUES(?,?,?,'App')").params(id, org, id.toString()).update();
        return id;
    }
    private tools.jackson.databind.JsonNode call(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder method,
            String jwt, Object body, int expected) throws Exception {
        var mapper = live.switchly.api.model.FlagValueValidator.getMapper();
        if (jwt != null) method.header("Authorization", "Bearer " + jwt);
        if (body != null) method.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body));
        var result = mockMvc.perform(method).andExpect(status().is(expected)).andReturn().getResponse();
        return mapper.readTree(result.getContentAsByteArray());
    }
    private java.util.Map<String,Object> assignment(UUID id) {return java.util.Map.of("role","EMPLOYEE","projectId",id);}
    private String invite(String email) throws Exception {
        return call(post(path+"/invitations"),token,java.util.Map.of("role","EMPLOYEE","projectId",project,"email",email),201).get("token").stringValue();
    }
    private java.util.Map<String,String> accept(String code,String email,String password) {
        return java.util.Map.of("token",code,"email",email,"fullName","Employee","password",password);
    }

    @Test void invitationIsSingleUseAndReassignmentTakesEffectOnIssuedTokens() throws Exception {
        String email = UUID.randomUUID()+"@example.org";
        var old = invite(email);
        var code = invite(email);
        call(post("/api/v1/auth/accept-invite"),null,accept(old,email,"Password123"),401);
        call(post("/api/v1/auth/accept-invite"),null,accept(code,"wrong@example.org","Password123"),401);
        var session = call(post("/api/v1/auth/accept-invite"),null,accept(code,email,"Password123"),200);
        var employee = session.get("accessToken").stringValue();
        String user = session.at("/user/id").stringValue();
        call(post("/api/v1/auth/accept-invite"),null,accept(code,email,"Password123"),401);
        assertThat(call(get(path),employee,null,200).size()).isEqualTo(1);
        assertThat(call(get(path),token,null,200).size()).isEqualTo(2);
        assertThat(call(get("/api/v1/orgs/"+org+"/projects"),employee,null,200).get(0).get("id").stringValue()).isEqualTo(project.toString());
        call(get("/api/v1/projects/"+otherProject),employee,null,403);
        call(post(path+"/invitations"),employee,java.util.Map.of("role","ADMIN","email","a@example.org"),403);
        call(put(path+"/"+user+"/role"),token,assignment(otherProject),200);
        call(get("/api/v1/projects/"+project),employee,null,403);
        call(get("/api/v1/projects/"+otherProject),employee,null,200);
        call(put(path+"/"+user+"/role"),token,java.util.Map.of("role","ADMIN"),200);
        call(get("/api/v1/projects/"+project),employee,null,200);
        call(delete(path+"/"+user),token,null,204);
        call(get("/api/v1/auth/me"),employee,null,401);
        call(post("/api/v1/auth/refresh"),null,java.util.Map.of("refreshToken",session.get("refreshToken").stringValue()),401);
    }

    @Test void existingIdentityAuthenticatesAndStaysWithinSelectedOrganizationAfterRefresh() throws Exception {
        String email=UUID.randomUUID()+"@example.org";
        var existing=users.create("Existing",email,"Password123");
        var other=testOrganizations.create("Other",existing.getId()).getId();
        var first=workspaceToken(existing,other.toString());
        var code=invite(email);
        call(post("/api/v1/auth/accept-invite"),null,accept(code,email,"wrongPassword"),401);
        var session=call(post("/api/v1/auth/accept-invite"),null,accept(code,email,"Password123"),200);
        call(get(path),first,null,403);
        var pair=call(post("/api/v1/auth/refresh"),null,java.util.Map.of("refreshToken",session.get("refreshToken").stringValue()),200);
        call(get("/api/v1/orgs/"+other),pair.get("accessToken").stringValue(),null,403);
        call(get("/api/v1/orgs/"+org),pair.get("accessToken").stringValue(),null,200);
        var choices=call(post("/api/v1/auth/login"),null,java.util.Map.of("email",email,"password","Password123"),409);
        assertThat(choices.get("organizations").size()).isEqualTo(2);
        assertThat(choices.has("accessToken")).isFalse();
        call(post("/api/v1/auth/login"),null,java.util.Map.of("email",email,"password","Password123","organizationId",other),200);
        call(post("/api/v1/auth/login"),null,java.util.Map.of("email",email,"password","Password123","organizationId",UUID.randomUUID()),401);
    }

    @Test void cancelledAndExpiredInvitationsCannotBeRedeemed() throws Exception {
        String email=UUID.randomUUID()+"@example.org";
        var code=invite(email);
        assertThat(call(get(path+"/invitations"),token,null,200).get(0).has("token")).isFalse();
        call(post(path+"/invitations/cancel"),token,java.util.Map.of("email",email),204);
        call(post("/api/v1/auth/accept-invite"),null,accept(code,email,"Password123"),401);
        code=invite(email);
        db.sql("UPDATE organization_invitations SET expires_at=now()-interval '1 day' WHERE organization_id=?").param(org).update();
        call(post("/api/v1/auth/accept-invite"),null,accept(code,email,"Password123"),401);
    }

    @Test void employeeCannotReadOrChangeSiblingProjectResourcesThroughDirectIds() throws Exception {
        String email=UUID.randomUUID()+"@example.org";
        var session=call(post("/api/v1/auth/accept-invite"),null,accept(invite(email),email,"Password123"),200);
        String employee=session.get("accessToken").stringValue();
        String sibling="/api/v1/projects/"+otherProject;
        var env=call(post(sibling+"/environments"),token,java.util.Map.of("name","Test","key","test"),201).get("id").stringValue();
        var flag=call(post(sibling+"/flags"),token,java.util.Map.of("name","Gate","key","gate"),201).get("id").stringValue();
        String environment="/api/v1/environments/"+env, config=environment+"/flags/"+flag;
        for (String resource : java.util.List.of(environment,environment+"/flags","/api/v1/flags/"+flag,config,
                config+"/targeting",config+"/rollout",sibling+"/segments",sibling+"/sdk-keys")) {
            call(get(resource),employee,null,403);
        }
        call(put(config+"/rollout"),employee,java.util.Map.of("percentage",25),403);
        call(put(config+"/targeting"),employee,java.util.List.of(),403);
        call(put(config+"/state"),employee,java.util.Map.of("enabled",true),403);
        call(post(config+"/preview"),employee,java.util.Map.of(),403);
        call(delete("/api/v1/flags/"+flag),employee,null,403);
        assertThat(call(get(config),token,null,200).get("enabled").booleanValue()).isFalse();
    }

    @Test void validatesRolesProjectsAndLastAdmin() throws Exception {
        call(delete(path+"/"+admin.getId()),token,null,409);
        call(put(path+"/"+admin.getId()+"/role"),token,assignment(project),409);
        for (var body : java.util.List.of(java.util.Map.of("role","EMPLOYEE","email","x@example.org"),
                java.util.Map.of("role","OWNER","email","x@example.org"),java.util.Map.of("role","ADMIN","projectId",project,"email","x@example.org"))) {
            call(post(path+"/invitations"),token,body,400);
        }
        var foreign=testOrganizations.create("Other",admin.getId()).getId();
        var foreignProject=UUID.randomUUID();
        db.sql("INSERT INTO projects(id,organization_id,key,name) VALUES(?,?,'a','App')").params(foreignProject,foreign).update();
        call(post(path+"/invitations"),token,java.util.Map.of("role","EMPLOYEE","email","x@example.org","projectId",foreignProject),403);
        call(post(path+"/invitations"),null,java.util.Map.of("role","ADMIN","email","x@example.org"),401);
    }
}
