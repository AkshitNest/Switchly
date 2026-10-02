package live.switchly.api.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrganizationProjectControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void organizationAndProjectLifecycle() throws Exception {
        mockMvc.perform(post("/api/v1/orgs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("VALIDATION_FAILED")));

        MvcResult orgResult = mockMvc.perform(post("/api/v1/orgs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Acme\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Acme")))
                .andReturn();
        String orgId = extractId(orgResult.getResponse().getContentAsString());

        mockMvc.perform(get("/api/v1/orgs/" + orgId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Acme")));

        mockMvc.perform(get("/api/v1/orgs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + orgId + "')].name").value("Acme"));

        mockMvc.perform(get("/api/v1/orgs/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("NOT_FOUND")));

        mockMvc.perform(post("/api/v1/orgs/" + UUID.randomUUID() + "/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Web\"}"))
                .andExpect(status().isNotFound());

        MvcResult projectResult = mockMvc.perform(post("/api/v1/orgs/" + orgId + "/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Web\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Web")))
                .andExpect(jsonPath("$.organizationId", is(orgId)))
                .andReturn();
        String projectId = extractId(projectResult.getResponse().getContentAsString());

        mockMvc.perform(get("/api/v1/projects/" + projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Web")));

        mockMvc.perform(get("/api/v1/orgs/" + orgId + "/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + projectId + "')].name").value("Web"));

        mockMvc.perform(get("/api/v1/projects/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("NOT_FOUND")));
    }

    private static String extractId(String json) {
        String search = "\"id\":\"";
        int start = json.indexOf(search) + search.length();
        return json.substring(start, json.indexOf("\"", start));
    }
}
