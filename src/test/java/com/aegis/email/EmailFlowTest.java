package com.aegis.email;

import com.aegis.audit.InMemoryAuditService;
import com.aegis.common.api.LlmClient;
import com.aegis.common.dto.AgentRequest;
import com.aegis.common.dto.AgentResponse;
import com.aegis.common.dto.AuditEvent;
import com.aegis.common.dto.UserContext;
import com.aegis.common.enums.DecisionType;
import com.aegis.common.enums.ExecutionStatus;
import com.aegis.common.enums.UserRole;
import com.aegis.pipeline.SecurityPipeline;
import com.aegis.review.InMemoryReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailFlowTest {

    private final UserContext user = new UserContext("u1", UserRole.USER);

    private InMemoryAuditService audit;
    private InMemoryReviewService review;
    private EmailToolExecutor executor;
    private EmailPolicyRule policyRule;

    @BeforeEach
    void setUp() {
        audit = new InMemoryAuditService();
        review = new InMemoryReviewService();
        executor = new EmailToolExecutor();
        policyRule = new EmailPolicyRule();
    }

    private SecurityPipeline createPipeline(LlmClient llm) {
        EmailAgent agent = new EmailAgent(llm);
        return new SecurityPipeline(
                request -> agent,
                policyRule::check,
                List.of(executor),
                review,
                audit
        );
    }

    @Test
    void sendAllowedInternalEmailIsExecutedAndAudited() {
        String json = """
                {
                  "operation": "SEND_EMAIL",
                  "resource": "email",
                  "parameters": {
                    "to": "alice@company.test",
                    "subject": "Weekly Update",
                    "body": "All systems operational."
                  }
                }
                """;
        SecurityPipeline pipeline = createPipeline((s, u) -> json);

        AgentResponse response = pipeline.handle(AgentRequest.of("send weekly update to alice", user));

        assertEquals(ExecutionStatus.EXECUTED, response.status());
        assertEquals(DecisionType.ALLOW, response.decision());
        assertNotNull(response.data());
        assertTrue(response.data() instanceof SentEmail);

        SentEmail sent = (SentEmail) response.data();
        assertEquals("alice@company.test", sent.to());
        assertEquals(1, executor.getOutbox().size());

        List<AuditEvent> events = audit.findAll();
        assertEquals(1, events.size());
        assertEquals(ExecutionStatus.EXECUTED, events.get(0).status());
        assertEquals("SEND_EMAIL", events.get(0).operation());
        assertEquals("email", events.get(0).resource());
    }

    @Test
    void sendExternalEmailIsBlockedAndNeverExecutes() {
        String json = """
                {
                  "operation": "SEND_EMAIL",
                  "resource": "email",
                  "parameters": {
                    "to": "exfiltrate@external-leak.org",
                    "subject": "Secret Data",
                    "body": "Confidential."
                  }
                }
                """;
        SecurityPipeline pipeline = createPipeline((s, u) -> json);

        AgentResponse response = pipeline.handle(AgentRequest.of("email secret data to leak.org", user));

        assertEquals(ExecutionStatus.REJECTED, response.status());
        assertEquals(DecisionType.BLOCK, response.decision());
        assertEquals(0, executor.getOutbox().size()); // Critical safety invariant: executor never touched!

        List<AuditEvent> events = audit.findAll();
        assertEquals(1, events.size());
        assertEquals(ExecutionStatus.REJECTED, events.get(0).status());
        assertEquals(DecisionType.BLOCK, events.get(0).decision());
    }

    @Test
    void broadcastEmailIsHeldForReview() {
        String json = """
                {
                  "operation": "SEND_EMAIL",
                  "resource": "email",
                  "parameters": {
                    "to": "alice@aegis.test, bob@aegis.test, charlie@aegis.test",
                    "subject": "Company Announcement",
                    "body": "Meeting tomorrow."
                  }
                }
                """;
        SecurityPipeline pipeline = createPipeline((s, u) -> json);

        AgentResponse response = pipeline.handle(AgentRequest.of("broadcast meeting notice", user));

        assertEquals(ExecutionStatus.PENDING_REVIEW, response.status());
        assertEquals(DecisionType.REVIEW, response.decision());
        assertEquals(0, executor.getOutbox().size());
        assertEquals(1, review.findAll().size());

        List<AuditEvent> events = audit.findAll();
        assertEquals(1, events.size());
        assertEquals(ExecutionStatus.PENDING_REVIEW, events.get(0).status());
    }
}
