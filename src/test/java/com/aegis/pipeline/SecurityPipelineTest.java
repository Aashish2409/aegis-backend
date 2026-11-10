package com.aegis.pipeline;

import com.aegis.audit.InMemoryAuditService;
import com.aegis.common.api.Agent;
import com.aegis.common.api.PolicyService;
import com.aegis.common.api.ToolExecutor;
import com.aegis.common.dto.AgentAction;
import com.aegis.common.dto.AgentRequest;
import com.aegis.common.dto.AgentResponse;
import com.aegis.common.dto.AuditEvent;
import com.aegis.common.dto.ExecutionResult;
import com.aegis.common.dto.PolicyResponse;
import com.aegis.common.dto.UserContext;
import com.aegis.common.enums.AgentType;
import com.aegis.common.enums.DecisionType;
import com.aegis.common.enums.ExecutionStatus;
import com.aegis.common.enums.UserRole;
// StubPolicyService removed in favor of test policy service
import com.aegis.review.InMemoryReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Checks the pipeline's core guarantees using fake agents and executors. */
class SecurityPipelineTest {

    private final UserContext user = new UserContext("u1", UserRole.USER);

    private InMemoryAuditService audit;
    private InMemoryReviewService review;
    private FakeExecutor executor;

    @BeforeEach
    void setUp() {
        audit = new InMemoryAuditService();
        review = new InMemoryReviewService();
        executor = new FakeExecutor();
    }

    private SecurityPipeline pipeline(Agent agent, PolicyService policy) {
        // The router is a one-method interface, so a lambda works.
        return new SecurityPipeline(request -> agent, policy, List.of(executor), review, audit);
    }

    private static PolicyService testPolicyService() {
        return request -> {
            String op = request.action().operation();
            if (op != null && (op.equalsIgnoreCase("READ") || op.equalsIgnoreCase("SEARCH") || op.equalsIgnoreCase("REPORT"))) {
                return PolicyResponse.allow("Operation allowed", "TEST-ALLOW");
            }
            return PolicyResponse.block("Operation blocked", "TEST-BLOCK");
        };
    }

    @Test
    void allowedActionIsExecutedAndAudited() {
        SecurityPipeline pipeline = pipeline(new FakeAgent("READ"), testPolicyService());

        AgentResponse response = pipeline.handle(AgentRequest.of("show IT employees", user));

        assertEquals(ExecutionStatus.EXECUTED, response.status());
        assertEquals(1, executor.calls);
        assertEquals(1, audit.findAll().size());
        assertEquals(ExecutionStatus.EXECUTED, audit.findAll().get(0).status());
    }

    @Test
    void blockedActionNeverReachesTheExecutor() {
        SecurityPipeline pipeline = pipeline(new FakeAgent("DELETE"), testPolicyService());

        AgentResponse response = pipeline.handle(AgentRequest.of("delete everyone", user));

        assertEquals(ExecutionStatus.REJECTED, response.status());
        assertEquals(0, executor.calls); // the key safety guarantee
        AuditEvent event = audit.findAll().get(0);
        assertEquals(DecisionType.BLOCK, event.decision());
        assertEquals(ExecutionStatus.REJECTED, event.status());
    }

    @Test
    void reviewDecisionIsQueuedNotExecuted() {
        PolicyService alwaysReview = request -> PolicyResponse.review("needs a human", "TEST-RULE");
        SecurityPipeline pipeline = pipeline(new FakeAgent("READ"), alwaysReview);

        AgentResponse response = pipeline.handle(AgentRequest.of("sensitive request", user));

        assertEquals(ExecutionStatus.PENDING_REVIEW, response.status());
        assertEquals(0, executor.calls);
        assertEquals(1, review.findAll().size());
        assertEquals(ExecutionStatus.PENDING_REVIEW, audit.findAll().get(0).status());
    }

    @Test
    void agentFailureFailsClosedAndIsAudited() {
        Agent brokenAgent = new FakeAgent("READ") {
            @Override
            public AgentAction propose(AgentRequest request) {
                throw new IllegalStateException("LLM is down");
            }
        };
        SecurityPipeline pipeline = pipeline(brokenAgent, testPolicyService());

        AgentResponse response = pipeline.handle(AgentRequest.of("anything", user));

        assertEquals(ExecutionStatus.FAILED, response.status());
        assertNull(response.data());
        assertEquals(0, executor.calls);
        assertEquals(ExecutionStatus.FAILED, audit.findAll().get(0).status());
    }

    // ---------- test helpers ----------

    /** Fake database agent that proposes a fixed operation. */
    private static class FakeAgent implements Agent {
        private final String operation;

        FakeAgent(String operation) {
            this.operation = operation;
        }

        @Override public String getAgentId() { return "DB_AGENT_01"; }
        @Override public String getAgentName() { return "Fake Database Agent"; }
        @Override public AgentType getType() { return AgentType.DATABASE; }

        @Override
        public AgentAction propose(AgentRequest request) {
            return new AgentAction(UUID.randomUUID().toString(), AgentType.DATABASE,
                    getAgentId(), operation, "employees", Map.of());
        }
    }

    /** Fake executor that counts how many times it was called. */
    private static class FakeExecutor implements ToolExecutor {
        int calls = 0;

        @Override public AgentType supports() { return AgentType.DATABASE; }

        @Override
        public ExecutionResult execute(AgentAction approvedAction) {
            calls++;
            return ExecutionResult.success("ok");
        }
    }
}
