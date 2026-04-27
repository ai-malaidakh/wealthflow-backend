package com.family.finance;

import com.family.finance.dto.RegisterRequest;
import com.family.finance.dto.TokenResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 3 integration tests: WatermelonDB push/pull round-trip.
 *
 * Endpoints under test:
 *   POST /api/sync/push?lastPulledAt=<ms>   — apply client changes
 *   GET  /api/sync/pull?lastPulledAt=<ms>   — fetch server changes
 *
 * Pass criteria (from MAL-21 exit criteria):
 *   1. Create on device A, sync, appears on device B
 *   2. Delete on device A, pull shows record in deleted list
 *   3. Tenant isolation: user A's records invisible to user B
 *   4. Unauthenticated requests rejected
 */
@SpringBootTest
@AutoConfigureMockMvc
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
        "app.jwt.refresh-token-expiry-ms=604800000",
        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC"
})
class SyncRoundTripTest {

    // H2 in PostgreSQL mode reads TIMESTAMP WITH TIME ZONE using the JVM's local
    // timezone. Force UTC so timestamp comparisons in buildPull are correct.
    private static TimeZone originalTimezone;

    @BeforeAll
    static void forceUtcTimezone() {
        originalTimezone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @AfterAll
    static void restoreTimezone() {
        TimeZone.setDefault(originalTimezone);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String registerAndGetToken(String email, String familyName) throws Exception {
        RegisterRequest reg = new RegisterRequest(email, "password123", "Test User", familyName);
        MvcResult result = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated())
                .andReturn();
        TokenResponse tokens = objectMapper.readValue(
                result.getResponse().getContentAsString(), TokenResponse.class);
        return tokens.accessToken();
    }

    /** Push changes for the accounts table. Returns the server timestamp. */
    private long pushAccountChanges(String token, long lastPulledAt,
                                    List<Map<String, Object>> created,
                                    List<Map<String, Object>> updated,
                                    List<String> deleted) throws Exception {
        Map<String, Object> body = Map.of(
                "lastPulledAt", lastPulledAt,
                "schemaVersion", 1,
                "changes", Map.of("accounts", Map.of(
                        "created", created,
                        "updated", updated,
                        "deleted", deleted
                ))
        );
        MvcResult result = mvc.perform(post("/api/sync/push?lastPulledAt=" + lastPulledAt)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timestamp").isNumber())
                .andReturn();

        @SuppressWarnings("unchecked")
        Map<String, Object> response = objectMapper.readValue(
                result.getResponse().getContentAsString(), Map.class);
        return ((Number) response.get("timestamp")).longValue();
    }

    /** Pull changes since lastPulledAt. Returns the full response. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> pullChanges(String token, long lastPulledAt) throws Exception {
        MvcResult result = mvc.perform(get("/api/sync/pull?lastPulledAt=" + lastPulledAt + "&schemaVersion=1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timestamp").isNumber())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> accountChangesFrom(Map<String, Object> pullResponse) {
        return (Map<String, Object>)
                ((Map<String, Object>) pullResponse.get("changes")).get("accounts");
    }

    /** Push changes for the transactions table given a pre-existing account. */
    private long pushTransactionChanges(String token, long lastPulledAt,
                                        List<Map<String, Object>> created,
                                        List<Map<String, Object>> updated,
                                        List<String> deleted) throws Exception {
        Map<String, Object> body = Map.of(
                "lastPulledAt", lastPulledAt,
                "schemaVersion", 1,
                "changes", Map.of("transactions", Map.of(
                        "created", created,
                        "updated", updated,
                        "deleted", deleted
                ))
        );
        MvcResult result = mvc.perform(post("/api/sync/push?lastPulledAt=" + lastPulledAt)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timestamp").isNumber())
                .andReturn();

        @SuppressWarnings("unchecked")
        Map<String, Object> response = objectMapper.readValue(
                result.getResponse().getContentAsString(), Map.class);
        return ((Number) response.get("timestamp")).longValue();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> transactionChangesFrom(Map<String, Object> pullResponse) {
        return (Map<String, Object>)
                ((Map<String, Object>) pullResponse.get("changes")).get("transactions");
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    /**
     * Push a new account record (created), then pull from epoch.
     * The record must survive the round-trip with matching fields.
     */
    @Test
    void createPushPullRoundTrip() throws Exception {
        String token = registerAndGetToken("sync1@example.com", "Sync Family 1");

        String clientId = UUID.randomUUID().toString();
        Map<String, Object> record = Map.of(
                "id", clientId,
                "name", "Main Checking",
                "type", "CHECKING",
                "balance", "1500.00",
                "currency", "USD"
        );

        // Push the create
        pushAccountChanges(token, 0L, List.of(record), List.of(), List.of());

        // Pull from epoch — should get the record back in 'created'
        Map<String, Object> pullResponse = pullChanges(token, 0L);
        Map<String, Object> accountChanges = accountChangesFrom(pullResponse);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pulledCreated = (List<Map<String, Object>>) accountChanges.get("created");
        assertThat(pulledCreated).isNotEmpty();

        Map<String, Object> pulledRecord = pulledCreated.stream()
                .filter(r -> clientId.equals(r.get("id")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected record id=" + clientId + " in pull created"));

        assertThat(pulledRecord.get("name")).isEqualTo("Main Checking");
        assertThat(pulledRecord.get("currency")).isEqualTo("USD");
        assertThat(pulledRecord.get("updated_at")).isNotNull();
    }

    /**
     * Push a create, record the server timestamp, then push a soft-delete.
     * A pull from before the delete must include the record in the deleted list.
     */
    @Test
    void deletePushAppearsInPullDeletedList() throws Exception {
        String token = registerAndGetToken("sync2@example.com", "Sync Family 2");

        String clientId = UUID.randomUUID().toString();
        Map<String, Object> record = Map.of(
                "id", clientId,
                "name", "Savings",
                "type", "SAVINGS",
                "balance", "5000.00",
                "currency", "EUR"
        );

        // Push create, capture timestamp
        long afterCreate = pushAccountChanges(token, 0L, List.of(record), List.of(), List.of());

        // Push delete
        pushAccountChanges(token, afterCreate, List.of(), List.of(), List.of(clientId));

        // Pull from before the create — record should appear in 'deleted'
        Map<String, Object> pullResponse = pullChanges(token, 0L);
        @SuppressWarnings("unchecked")
        List<String> deleted = (List<String>) accountChangesFrom(pullResponse).get("deleted");
        assertThat(deleted).contains(clientId);
    }

    /**
     * Tenant isolation: User A's records must not appear in User B's pull.
     */
    @Test
    void syncIsTenantIsolated() throws Exception {
        String tokenA = registerAndGetToken("synca@example.com", "Alpha Sync Family");
        String tokenB = registerAndGetToken("syncb@example.com", "Beta Sync Family");

        String recordId = UUID.randomUUID().toString();
        Map<String, Object> record = Map.of(
                "id", recordId,
                "name", "Private Account",
                "type", "CHECKING",
                "balance", "999.00",
                "currency", "USD"
        );

        // User A pushes a create
        pushAccountChanges(tokenA, 0L, List.of(record), List.of(), List.of());

        // User B pulls — must not see User A's record
        Map<String, Object> pullResponse = pullChanges(tokenB, 0L);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> createdForB =
                (List<Map<String, Object>>) accountChangesFrom(pullResponse).get("created");

        boolean userARecordVisible = createdForB.stream()
                .anyMatch(r -> recordId.equals(r.get("id")));
        assertThat(userARecordVisible)
                .as("User A's record must not be visible in User B's pull")
                .isFalse();
    }

    /**
     * Sync endpoints must reject unauthenticated requests.
     */
    @Test
    void syncRequiresAuthentication() throws Exception {
        mvc.perform(post("/api/sync/push")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/sync/pull"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Regression: transaction date must round-trip as ISO string ("2026-04-26"), not epoch ms.
     *
     * WatermelonDB schema defines the `date` column as type:'string'. If the backend
     * sends a Long, WatermelonDB throws a type-validation error in dev mode and the
     * entire synchronize() call fails — no server data ever appears on device.
     */
    @Test
    void transactionDateRoundTripsAsIsoString() throws Exception {
        String token = registerAndGetToken("txdate@example.com", "TxDate Family");

        // Push an account first — transactions reference it by account_id
        String accountId = UUID.randomUUID().toString();
        long afterAccount = pushAccountChanges(token, 0L,
                List.of(Map.of("id", accountId, "name", "Checking",
                        "type", "CHECKING", "balance", "1000.00", "currency", "USD")),
                List.of(), List.of());

        // Push a transaction with an ISO date string (exactly how the mobile writes it)
        String txId = UUID.randomUUID().toString();
        pushTransactionChanges(token, afterAccount,
                List.of(Map.of(
                        "id", txId,
                        "account_id", accountId,
                        "amount", "50.00",
                        "currency", "USD",
                        "date", "2026-04-26",
                        "version", 0
                )),
                List.of(), List.of());

        // Pull from epoch — the transaction must come back with date as a String, not a Long
        Map<String, Object> pullResponse = pullChanges(token, 0L);
        Map<String, Object> txChanges = transactionChangesFrom(pullResponse);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> created = (List<Map<String, Object>>) txChanges.get("created");
        assertThat(created).isNotEmpty();

        Map<String, Object> pulled = created.stream()
                .filter(r -> txId.equals(r.get("id")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Transaction not found in pull created"));

        Object dateValue = pulled.get("date");
        assertThat(dateValue)
                .as("date must be a String (ISO-8601), not a Long — WatermelonDB schema is type:'string'")
                .isInstanceOf(String.class);
        assertThat((String) dateValue).isEqualTo("2026-04-26");
    }

    /**
     * Version conflict: server version > client version → server wins, conflict logged.
     * Client push is silently rejected; server record unchanged in pull.
     */
    @Test
    void versionConflictServerWins() throws Exception {
        String token = registerAndGetToken("conflict1@example.com", "Conflict Family 1");

        String clientId = UUID.randomUUID().toString();
        Map<String, Object> original = Map.of(
                "id", clientId,
                "name", "Original Name",
                "type", "CHECKING",
                "balance", "100.00",
                "currency", "USD",
                "version", 0
        );

        // Push create (version 0)
        long afterCreate = pushAccountChanges(token, 0L, List.of(original), List.of(), List.of());

        // Simulate server-side update bumping version (a second client updates)
        Map<String, Object> serverUpdate = Map.of(
                "id", clientId,
                "name", "Server Updated Name",
                "type", "CHECKING",
                "balance", "200.00",
                "currency", "USD",
                "version", 1
        );
        long afterUpdate = pushAccountChanges(token, afterCreate, List.of(), List.of(serverUpdate), List.of());

        // Stale client (version 0) tries to push — server version is now 1, so server wins
        Map<String, Object> staleUpdate = Map.of(
                "id", clientId,
                "name", "Stale Client Name",
                "type", "CHECKING",
                "balance", "150.00",
                "currency", "USD",
                "version", 0   // ← stale
        );
        pushAccountChanges(token, 0L, List.of(), List.of(staleUpdate), List.of());

        // Pull from afterUpdate — account was created before afterUpdate, so lands in `updated`.
        // Server's name must win over the stale push.
        Map<String, Object> pullResponse = pullChanges(token, afterUpdate);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> updated =
                (List<Map<String, Object>>) accountChangesFrom(pullResponse).get("updated");

        Map<String, Object> pulledRecord = updated.stream()
                .filter(r -> clientId.equals(r.get("id")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Record not found in pull updated"));

        assertThat(pulledRecord.get("name"))
                .as("Server version should win over stale client push")
                .isEqualTo("Server Updated Name");
    }
}
