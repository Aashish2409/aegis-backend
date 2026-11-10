package com.aegis.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.aegis.audit.InMemoryAuditService;
import com.aegis.common.api.CurrentUserProvider;
import com.aegis.common.api.PolicyService;
import com.aegis.common.api.ToolExecutor;
import com.aegis.common.dto.AgentAction;
import com.aegis.common.dto.AgentResponse;
import com.aegis.common.dto.ExecutionResult;
import com.aegis.common.dto.PolicyResponse;
import com.aegis.common.dto.ReviewItem;
import com.aegis.common.dto.UserContext;
import com.aegis.common.enums.AgentType;
import com.aegis.common.enums.DecisionType;
import com.aegis.common.enums.ExecutionStatus;
import com.aegis.common.enums.ReviewStatus;
import com.aegis.common.enums.UserRole;
import com.aegis.pipeline.SecurityPipeline;

class ReviewControllerTest {

    private InMemoryReviewService reviewService;
    private InMemoryAuditService auditService;
    private SecurityPipeline pipeline;
    private ReviewController controller;

    private final UserContext reviewer = new UserContext("reviewer1", UserRole.REVIEWER);
    private final CurrentUserProvider userProvider = () -> reviewer;

    private static class MockExecutor implements ToolExecutor {
        @Override public AgentType supports() { return AgentType.DATABASE; }
        @Override public ExecutionResult execute(AgentAction action) {
            return ExecutionResult.success(Map.of("message", "Executed"));
        }
    }

    @BeforeEach
    void setUp() {
        reviewService = new InMemoryReviewService();
        auditService = new InMemoryAuditService();
        PolicyService policy = req -> PolicyResponse.allow("ok", "TEST");
        pipeline = new SecurityPipeline(req -> null, policy, List.of(new MockExecutor()), reviewService, auditService);
        controller = new ReviewController(reviewService, pipeline, userProvider);
    }

    @Test
    void listsPendingReviewItems() {
        AgentAction action = new AgentAction(UUID.randomUUID().toString(), AgentType.DATABASE, "DB",
                "UPDATE", "employees", Map.of());
        reviewService.queue(action, new UserContext("u1", UserRole.USER));

        List<ReviewItem> pending = controller.pending();
        assertEquals(1, pending.size());
        assertEquals(ReviewStatus.PENDING, pending.get(0).status());
    }

    @Test
    void approvesAndExecutesAction() {
        AgentAction action = new AgentAction(UUID.randomUUID().toString(), AgentType.DATABASE, "DB",
                "UPDATE", "employees", Map.of());
        ReviewItem item = reviewService.queue(action, new UserContext("u1", UserRole.USER));

        ResponseEntity<?> res = controller.decide(item.reviewId(),
                new ReviewController.DecisionPayload(true, "Looks good"));

        assertEquals(HttpStatus.OK, res.getStatusCode());
        AgentResponse agentResponse = (AgentResponse) res.getBody();
        assertNotNull(agentResponse);
        assertEquals(ExecutionStatus.EXECUTED, agentResponse.status());
        assertEquals(ReviewStatus.APPROVED, reviewService.findById(item.reviewId()).orElseThrow().status());
    }

    @Test
    void rejectsAction() {
        AgentAction action = new AgentAction(UUID.randomUUID().toString(), AgentType.DATABASE, "DB",
                "UPDATE", "employees", Map.of());
        ReviewItem item = reviewService.queue(action, new UserContext("u1", UserRole.USER));

        ResponseEntity<?> res = controller.decide(item.reviewId(),
                new ReviewController.DecisionPayload(false, "Unsafe operation"));

        assertEquals(HttpStatus.FORBIDDEN, res.getStatusCode());
        AgentResponse agentResponse = (AgentResponse) res.getBody();
        assertNotNull(agentResponse);
        assertEquals(ExecutionStatus.REJECTED, agentResponse.status());
        assertEquals(ReviewStatus.REJECTED, reviewService.findById(item.reviewId()).orElseThrow().status());
    }
}
