package com.family.finance;

import com.family.finance.dto.LoginRequest;
import com.family.finance.dto.RegisterRequest;
import com.family.finance.dto.TokenResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.jwt.secret=test-secret-key-at-least-256-bits-long-for-hmac-sha256-aaaaa",
        "app.jwt.access-token-expiry-ms=900000",
        "app.jwt.refresh-token-expiry-ms=604800000"
})
public class AuthSmokeTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void healthEndpointReturns200() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void registerCreatesUserAndReturnsTokens() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "alice@example.com", "password123", "Alice", "Smith Family");

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.familyIds").isArray());
    }

    @Test
    void loginReturnsTokensAfterRegister() throws Exception {
        // Register first
        RegisterRequest reg = new RegisterRequest(
                "bob@example.com", "password123", "Bob", "Johnson Family");
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated());

        // Now login
        LoginRequest login = new LoginRequest("bob@example.com", "password123");
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.familyIds").isArray());
    }

    @Test
    void accountsEndpointRequiresAuth() throws Exception {
        mvc.perform(get("/api/accounts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUserCanListAccounts() throws Exception {
        // Register
        RegisterRequest reg = new RegisterRequest(
                "carol@example.com", "password123", "Carol", "Williams Family");
        MvcResult result = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andReturn();

        TokenResponse tokens = objectMapper.readValue(
                result.getResponse().getContentAsString(), TokenResponse.class);

        // Access accounts with token
        mvc.perform(get("/api/accounts")
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    /**
     * Core security test: user A's accounts must never appear in user B's account list.
     * Two independent families, each with one account — cross-tenant reads must return empty.
     */
    @Test
    void userACannotSeeUserBAccounts() throws Exception {
        // Register user A in "Alpha Family"
        RegisterRequest regA = new RegisterRequest(
                "usera@example.com", "password123", "UserA", "Alpha Family");
        MvcResult resultA = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regA)))
                .andReturn();
        TokenResponse tokensA = objectMapper.readValue(
                resultA.getResponse().getContentAsString(), TokenResponse.class);

        // User A creates a personal account
        String accountPayload = """
                {"name":"Alice Checking","type":"CHECKING","balance":1000.00,"currency":"USD","familyId":null}
                """;
        mvc.perform(post("/api/accounts")
                        .header("Authorization", "Bearer " + tokensA.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountPayload))
                .andExpect(status().isCreated());

        // Register user B in a completely different family
        RegisterRequest regB = new RegisterRequest(
                "userb@example.com", "password123", "UserB", "Beta Family");
        MvcResult resultB = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regB)))
                .andReturn();
        TokenResponse tokensB = objectMapper.readValue(
                resultB.getResponse().getContentAsString(), TokenResponse.class);

        // User B lists accounts — must be empty (cannot see user A's account)
        mvc.perform(get("/api/accounts")
                        .header("Authorization", "Bearer " + tokensB.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
