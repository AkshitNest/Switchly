package live.switchly.api.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FlagControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private String projectId;

    @BeforeEach
    void setUp() throws Exception {
        // Create an organization
        MvcResult orgResult = mockMvc.perform(post("/api/v1/orgs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Acme Corp\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        String orgJson = orgResult.getResponse().getContentAsString();
        // Extract orgId from JSON response: {"id":"...", ...}
        String orgId = extractJsonField(orgJson, "id");

        // Create a project under the organization
        MvcResult projectResult = mockMvc.perform(post("/api/v1/orgs/" + orgId + "/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Web App\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        String projectJson = projectResult.getResponse().getContentAsString();
        this.projectId = extractJsonField(projectJson, "id");
    }

    @Test
    void createFlag_withDescription_returnsCreatedWithDescription() throws Exception {
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/flags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "key": "new-checkout-flow",
                                    "name": "New Checkout Flow",
                                    "description": "Enables the redesigned 3-step checkout process"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key", is("new-checkout-flow")))
                .andExpect(jsonPath("$.name", is("New Checkout Flow")))
                .andExpect(jsonPath("$.description", is("Enables the redesigned 3-step checkout process")))
                .andExpect(jsonPath("$.enabled", is(false)));
    }

    @Test
    void createFlag_withoutDescription_returnsCreatedWithNullDescription() throws Exception {
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/flags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "key": "legacy-export",
                                    "name": "Legacy Export"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key", is("legacy-export")))
                .andExpect(jsonPath("$.name", is("Legacy Export")))
                .andExpect(jsonPath("$.description", nullValue()))
                .andExpect(jsonPath("$.enabled", is(false)));
    }

    @Test
    void deleteFlag_existingFlag_returns204NoContent_andSubsequentGetReturns404() throws Exception {
        // Create flag first
        MvcResult createResult = mockMvc.perform(post("/api/v1/projects/" + projectId + "/flags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "key": "temp-banner",
                                    "name": "Temporary Banner",
                                    "description": "To be removed after campaign"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        String flagId = extractJsonField(createResult.getResponse().getContentAsString(), "id");

        // Verify it can be retrieved
        mockMvc.perform(get("/api/v1/flags/" + flagId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description", is("To be removed after campaign")));

        // Delete the flag
        mockMvc.perform(delete("/api/v1/flags/" + flagId))
                .andExpect(status().isNoContent())
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentAsString()).isEmpty());

        // Subsequent get should return 404
        mockMvc.perform(get("/api/v1/flags/" + flagId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("NOT_FOUND")));
    }

    @Test
    void deleteFlag_nonExistentFlag_returns404NotFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/flags/" + nonExistentId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("NOT_FOUND")))
                .andExpect(jsonPath("$.error.message", containsString(nonExistentId.toString())));
    }

    @Test
    void listToggleAndGetFlag() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/projects/" + projectId + "/flags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"key": "toggle-me", "name": "Toggle Me"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String flagId = extractJsonField(createResult.getResponse().getContentAsString(), "id");

        mockMvc.perform(get("/api/v1/projects/" + projectId + "/flags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + flagId + "')].key").value("toggle-me"));

        mockMvc.perform(put("/api/v1/flags/" + flagId + "/state")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled", is(true)));

        mockMvc.perform(get("/api/v1/flags/" + flagId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled", is(true)));

        mockMvc.perform(post("/api/v1/projects/" + projectId + "/flags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\": \"BAD KEY\", \"name\": \"Invalid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("VALIDATION_FAILED")));
    }

    @Test
    void createFlag_duplicateKey_returns409Conflict() throws Exception {
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/flags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "key": "unique-flag",
                                    "name": "Unique Flag"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/projects/" + projectId + "/flags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "key": "unique-flag",
                                    "name": "Duplicate Unique Flag"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("CONFLICT")));
    }

    private static String extractJsonField(String json, String field) {
        String search = "\"" + field + "\":\"";
        int start = json.indexOf(search);
        if (start == -1) {
            throw new IllegalArgumentException("Field " + field + " not found in json: " + json);
        }
        start += search.length();
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }
}
