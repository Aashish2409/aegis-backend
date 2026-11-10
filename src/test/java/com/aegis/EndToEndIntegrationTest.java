package com.aegis;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.aegis.auth.JwtService;
import com.aegis.common.api.AuditService;
import com.aegis.common.api.ReviewService;
import com.aegis.common.dto.AgentAction;
import com.aegis.common.dto.AuditEvent;
import com.aegis.common.dto.ReviewItem;
import com.aegis.common.dto.UserContext;
import com.aegis.common.enums.AgentType;
import com.aegis.common.enums.DecisionType;
import com.aegis.common.enums.ExecutionStatus;
import com.aegis.common.enums.UserRole;

/**
 * Full End-to-End integration test covering the milestone requirements and cross-agent flows.
 */
@SpringBootTest(properties = "aegis.jwt.secret=test-only-secret-change-me-0123456789abcdef")
class EndToEndIntegrationTest {

    @Autowired WebApplicationContext context;
    @Autowired JwtService jwtService;
    @Autowired AuditService auditService;
    @Autowired ReviewService reviewService;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private String token(String username, UserRole role) {
        return jwtService.generate(new com.aegis.auth.User(username, username, username + "@aegis.internal", "pw", role));
    }

    @Test
    void milestoneShowEmployeesReturns200AndAudited() throws Exception {
        String token = token("user1", UserRole.USER);

        mvc.perform(post("/api/requests")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"show IT employees\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXECUTED"))
                .andExpect(jsonPath("$.decision").value("ALLOW"));

        List<AuditEvent> logs = auditService.findAll();
        assertFalse(logs.isEmpty());
        AuditEvent last = logs.get(logs.size() - 1);
        assertEquals(ExecutionStatus.EXECUTED, last.status());
        assertEquals(DecisionType.ALLOW, last.decision());
    }

    @Test
    void milestoneDeleteEmployeesReturns403AndAudited() throws Exception {
        String token = token("user2", UserRole.USER);

        mvc.perform(post("/api/requests")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"delete all employees\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.decision").value("BLOCK"));

        List<AuditEvent> logs = auditService.findAll();
        AuditEvent last = logs.get(logs.size() - 1);
        assertEquals(ExecutionStatus.REJECTED, last.status());
        assertEquals(DecisionType.BLOCK, last.decision());
    }

    @Test
    void reviewerCanAccessReviewEndpoints() throws Exception {
        String userToken = token("regular_user", UserRole.USER);
        String reviewerToken = token("reviewer_lead", UserRole.REVIEWER);

        // Queue a review item
        AgentAction action = new AgentAction(UUID.randomUUID().toString(), AgentType.DATABASE, "DB",
                "REPORT", "employees", Map.of());
        ReviewItem item = reviewService.queue(action, new UserContext("regular_user", UserRole.USER));

        // Regular user is forbidden
        mvc.perform(get("/api/review/pending")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        // Reviewer can list pending
        mvc.perform(get("/api/review/pending")
                        .header("Authorization", "Bearer " + reviewerToken))
                .andExpect(status().isOk());

        // Reviewer can approve item
        mvc.perform(post("/api/review/" + item.reviewId() + "/decide")
                        .header("Authorization", "Bearer " + reviewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true,\"reason\":\"Approved for reporting\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXECUTED"));
    }

    @Test
    void auditStatsEndpointReturnsAggregates() throws Exception {
        String reviewerToken = token("auditor1", UserRole.REVIEWER);

        mvc.perform(get("/api/audit/stats")
                        .header("Authorization", "Bearer " + reviewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequests").isNumber());
    }
}
