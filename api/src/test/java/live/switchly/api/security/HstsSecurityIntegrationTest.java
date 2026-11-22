package live.switchly.api.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class HstsSecurityIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RateLimitProperties rateLimitProperties;

    @BeforeEach
    void setup() {
        rateLimitProperties.setTrustedProxies(List.of("10.0.0.1", "127.0.0.1"));
    }

    @Test
    @DisplayName("Direct HTTPS request emits Strict-Transport-Security header")
    void directHttps_emitsHstsHeader() throws Exception {
        mockMvc.perform(get("/healthz").secure(true))
                .andExpect(status().isOk())
                .andExpect(header().string("Strict-Transport-Security", "max-age=31536000 ; includeSubDomains"));
    }

    @Test
    @DisplayName("Plain HTTP request does NOT emit Strict-Transport-Security header")
    void plainHttp_doesNotEmitHstsHeader() throws Exception {
        mockMvc.perform(get("/healthz").secure(false))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Strict-Transport-Security"));
    }

    @Test
    @DisplayName("Plain HTTP with X-Forwarded-Proto from UNTRUSTED client does NOT emit HSTS")
    void untrustedClient_withForwardedProto_doesNotEmitHsts() throws Exception {
        mockMvc.perform(get("/healthz")
                        .secure(false)
                        .header("X-Forwarded-Proto", "https")
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.195"); // untrusted client IP
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Strict-Transport-Security"));
    }

    @Test
    @DisplayName("Request from TRUSTED reverse proxy with X-Forwarded-Proto: https emits HSTS")
    void trustedProxy_withHttpsForwardedProto_emitsHsts() throws Exception {
        mockMvc.perform(get("/healthz")
                        .secure(false)
                        .header("X-Forwarded-Proto", "https")
                        .with(request -> {
                            request.setRemoteAddr("10.0.0.1"); // trusted reverse proxy IP
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(header().string("Strict-Transport-Security", "max-age=31536000 ; includeSubDomains"));
    }

    @Test
    @DisplayName("Request from TRUSTED reverse proxy with X-Forwarded-Proto: http does NOT emit HSTS")
    void trustedProxy_withHttpForwardedProto_doesNotEmitHsts() throws Exception {
        mockMvc.perform(get("/healthz")
                        .secure(false)
                        .header("X-Forwarded-Proto", "http")
                        .with(request -> {
                            request.setRemoteAddr("10.0.0.1"); // trusted proxy, but client used plain http
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Strict-Transport-Security"));
    }
}

