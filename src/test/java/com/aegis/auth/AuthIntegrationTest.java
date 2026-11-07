package com.aegis.auth;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.aegis.common.api.AuditService;
import com.aegis.common.dto.AuditEvent;
import com.aegis.common.enums.DecisionType;
import com.aegis.common.enums.UserRole;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

@SpringBootTest(properties = "aegis.jwt.secret=test-only-secret-change-me-0123456789abcdef")
class AuthIntegrationTest {

    @Autowired WebApplicationContext context;
    @Autowired JwtService jwtService;
    @Autowired JwtEncoder jwtEncoder;
    @Autowired AuditService auditService;

    MockMvc mvc;

    @SuppressWarnings("unused")
    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String bearer(String userId, UserRole role) {
        return "Bearer " + jwtService.generate(new User(userId, "x", "x@x.com", "h", role));
    }

    private static String message(String text) {
        return "{\"message\":\"" + text + "\"}";
    }

    private List<AuditEvent> auditFor(String userId) {
        return auditService.findAll().stream().filter(e -> userId.equals(e.userId())).toList();
    }

    // ---------- registration & login ----------

    @Test
    void registerSucceedsAndIgnoresRoleInBody() throws Exception {
        String name = unique("alice");
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + name + "\",\"email\":\"" + name
                                + "@aegis.test\",\"password\":\"password123\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isCreated())
                .andExpect(content().string(containsString("\"role\":\"USER\"")))
                .andExpect(content().string(not(containsString("password"))));
    }

    @Test
    void duplicateRegistrationIsRejected() throws Exception {
        String name = unique("bob");
        String body = "{\"username\":\"" + name + "\",\"email\":\"" + name
                + "@aegis.test\",\"password\":\"password123\"}";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
        // same email, different username
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + name + "2\",\"email\":\"" + name
                                + "@aegis.test\",\"password\":\"password123\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidRegistrationInputIsRejected() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"a\",\"email\":\"nope\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginSucceedsAndInvalidLoginIsRejected() throws Exception {
        String name = unique("carol");
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + name + "\",\"email\":\"" + name
                        + "@aegis.test\",\"password\":\"password123\"}")).andExpect(status().isCreated());

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + name + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"token\"")));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + name + "\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nobody-here\",\"password\":\"password123\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- token validation ----------

    @Test
    void missingGarbageForgedAndExpiredTokensGet401() throws Exception {
        mvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON)
                .content(message("show IT employees"))).andExpect(status().isUnauthorized());

        mvc.perform(post("/api/requests").header("Authorization", "Bearer not.a.jwt")
                .contentType(MediaType.APPLICATION_JSON).content(message("show IT employees")))
                .andExpect(status().isUnauthorized());

        // signed with a different secret
        JwtEncoder forger = new NimbusJwtEncoder(new ImmutableSecret<>(
                new SecretKeySpec("a-completely-different-secret-0123456789".getBytes(), "HmacSHA256")));
        Instant now = Instant.now();
        JwtClaimsSet forgedClaims = JwtClaimsSet.builder().issuer("aegis").subject("hacker")
                .issuedAt(now).expiresAt(now.plus(1, ChronoUnit.HOURS)).claim("role", "ADMIN").build();
        String forged = forger.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), forgedClaims)).getTokenValue();
        mvc.perform(post("/api/requests").header("Authorization", "Bearer " + forged)
                .contentType(MediaType.APPLICATION_JSON).content(message("show IT employees")))
                .andExpect(status().isUnauthorized());

        // correctly signed but expired (well beyond the 60s default clock skew)
        JwtClaimsSet expiredClaims = JwtClaimsSet.builder().issuer("aegis").subject("u-old")
                .issuedAt(now.minus(30, ChronoUnit.MINUTES)).expiresAt(now.minus(20, ChronoUnit.MINUTES))
                .claim("role", "USER").build();
        String expired = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), expiredClaims)).getTokenValue();
        mvc.perform(post("/api/requests").header("Authorization", "Bearer " + expired)
                .contentType(MediaType.APPLICATION_JSON).content(message("show IT employees")))
                .andExpect(status().isUnauthorized());
    }

    // ---------- pipeline integration ----------

    @Test
    void validTokenRunsThePipelineWithTheTokenIdentity() throws Exception {
        String userId = unique("user");
        mvc.perform(post("/api/requests").header("Authorization", bearer(userId, UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON).content(message("show IT employees")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Rohan Das")))
                .andExpect(content().string(not(containsString("Meera Iyer"))));

        List<AuditEvent> events = auditFor(userId);
        assertEquals(1, events.size());
        assertEquals("READ", events.get(0).operation());
    }

    @Test
    void bodyCannotOverrideAuthenticatedIdentity() throws Exception {
        String userId = unique("real");
        mvc.perform(post("/api/requests").header("Authorization", bearer(userId, UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"show IT employees\",\"userId\":\"admin\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk());

        assertEquals(1, auditFor(userId).size());
        assertTrue(auditFor("admin").isEmpty());
    }

    @Test
    void deleteIsForbiddenAndAuditedAsBlocked() throws Exception {
        String userId = unique("evil");
        mvc.perform(post("/api/requests").header("Authorization", bearer(userId, UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON).content(message("delete all employees")))
                .andExpect(status().isForbidden());

        AuditEvent event = auditFor(userId).get(0);
        assertEquals(DecisionType.BLOCK, event.decision());
        assertEquals("DELETE", event.operation());
    }

    // ---------- roles ----------

    @Test
    void auditLogsAreRestrictedToReviewerAndAdmin() throws Exception {
        mvc.perform(get("/api/audit/logs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/audit/logs").header("Authorization", bearer("u1", UserRole.USER)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/audit/logs").header("Authorization", bearer("u2", UserRole.REVIEWER)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/audit/logs").header("Authorization", bearer("u3", UserRole.ADMIN)))
                .andExpect(status().isOk());
    }

    @Test
    void onlyAdminCanChangeRoles() throws Exception {
        String name = unique("dave");
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + name + "\",\"email\":\"" + name
                        + "@aegis.test\",\"password\":\"password123\"}")).andExpect(status().isCreated());

        mvc.perform(put("/api/admin/users/" + name + "/role").header("Authorization", bearer("u1", UserRole.REVIEWER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/users/" + name + "/role").header("Authorization", bearer("u3", UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"REVIEWER\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"role\":\"REVIEWER\"")));
    }
}