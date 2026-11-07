package com.aegis.database;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.aegis.audit.InMemoryAuditService;
import com.aegis.common.dto.AgentRequest;
import com.aegis.common.dto.AgentResponse;
import com.aegis.common.dto.AuditEvent;
import com.aegis.common.dto.UserContext;
import com.aegis.common.enums.DecisionType;
import com.aegis.common.enums.ExecutionStatus;
import com.aegis.common.enums.UserRole;
import com.aegis.common.llm.MockLlmClient;
import com.aegis.pipeline.SecurityPipeline;
import com.aegis.review.InMemoryReviewService;

class DatabaseFlowTest {

    private final UserContext user = new UserContext("u1", UserRole.USER);
    private InMemoryAuditService audit;
    private SecurityPipeline pipeline;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        audit = new InMemoryAuditService();
        DatabaseAgent agent = new DatabaseAgent(new MockLlmClient());
        DatabasePolicyRule rule = new DatabasePolicyRule();
        pipeline = new SecurityPipeline(request -> agent, rule::check,
                List.of(new DatabaseToolExecutor()), new InMemoryReviewService(), audit);
    }

    @Test
    @SuppressWarnings("unchecked")
    void showItEmployeesIsExecutedAndAudited() {
        AgentResponse response = pipeline.handle(AgentRequest.of("show IT employees", user));

        assertEquals(ExecutionStatus.EXECUTED, response.status());
        List<Employee> data = (List<Employee>) response.data();
        assertEquals(3, data.size());
        assertTrue(data.stream().allMatch(e -> e.department().equals("IT")));
        assertEquals(ExecutionStatus.EXECUTED, audit.findAll().get(0).status());
    }

    @Test
    void deleteIsBlockedAndAudited() {
        AgentResponse response = pipeline.handle(AgentRequest.of("delete all employees", user));

        assertEquals(ExecutionStatus.REJECTED, response.status());
        AuditEvent event = audit.findAll().get(0);
        assertEquals(DecisionType.BLOCK, event.decision());
        assertEquals("DELETE", event.operation());
        assertEquals("employees", event.resource());
    }
}