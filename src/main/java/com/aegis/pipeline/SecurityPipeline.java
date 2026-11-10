package com.aegis.pipeline;

import com.aegis.common.api.Agent;
import com.aegis.common.api.AgentRouter;
import com.aegis.common.api.AuditService;
import com.aegis.common.api.PolicyService;
import com.aegis.common.api.ReviewService;
import com.aegis.common.api.ToolExecutor;
import com.aegis.common.dto.AgentAction;
import com.aegis.common.dto.AgentRequest;
import com.aegis.common.dto.AgentResponse;
import com.aegis.common.dto.AuditEvent;
import com.aegis.common.dto.ExecutionResult;
import com.aegis.common.dto.PolicyRequest;
import com.aegis.common.dto.PolicyResponse;
import com.aegis.common.dto.ReviewItem;
import com.aegis.common.dto.UserContext;
import com.aegis.common.enums.AgentType;
import com.aegis.common.enums.DecisionType;
import com.aegis.common.enums.ExecutionStatus;
import com.aegis.common.enums.ReviewStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * THE central security pipeline (owner: Aashish).
 *
 * Every request goes through this one class, in this order:
 *   1. router picks an agent
 *   2. the agent PROPOSES an action (it cannot run anything)
 *   3. the Policy Engine decides ALLOW / BLOCK / REVIEW
 *   4. ALLOW -> executor runs it | REVIEW -> queued | BLOCK -> rejected
 *   5. the outcome is ALWAYS written to the audit log
 *
 * Fail-closed: if anything goes wrong (agent, LLM, policy, executor), nothing
 * is executed and the failure is audited.
 */
@Service
public class SecurityPipeline {

    private final AgentRouter router;
    private final PolicyService policyService;
    private final Map<AgentType, ToolExecutor> executors = new EnumMap<>(AgentType.class);
    private final ReviewService reviewService;
    private final AuditService auditService;

    public SecurityPipeline(AgentRouter router,
                            PolicyService policyService,
                            List<ToolExecutor> executorList,
                            ReviewService reviewService,
                            AuditService auditService) {
        this.router = router;
        this.policyService = policyService;
        this.reviewService = reviewService;
        this.auditService = auditService;

        // Index executors by agent type. Two executors for one type is a
        // mistake, so fail at startup instead of picking one silently.
        for (ToolExecutor executor : executorList) {
            if (this.executors.put(executor.supports(), executor) != null) {
                throw new IllegalStateException("More than one ToolExecutor for " + executor.supports());
            }
        }
    }

    /** Runs one request through the whole security flow. Never throws. */
    public AgentResponse handle(AgentRequest request) {
        AgentAction action = null;
        PolicyResponse policy = null;
        try {
            Agent agent = router.route(request);
            action = agent.propose(request);
            policy = policyService.evaluate(new PolicyRequest(action, request.user()));

            // A null decision throws here and lands in the catch: fail closed.
            return switch (policy.decision()) {
                case ALLOW -> executeApproved(request, action, policy);
                case REVIEW -> queueForReview(request, action, policy);
                case BLOCK -> reject(request, action, policy);
            };
        } catch (Exception e) {
            return fail(request, action, policy, e);
        }
    }

    private AgentResponse executeApproved(AgentRequest request, AgentAction action, PolicyResponse policy) {
        ToolExecutor executor = executors.get(action.agentType());
        if (executor == null) {
            throw new IllegalStateException("No executor for " + action.agentType());
        }
        ExecutionResult result = executor.execute(action);

        if (result.success()) {
            audit(request, action, policy, ExecutionStatus.EXECUTED, policy.reason());
            return new AgentResponse(request.requestId(), ExecutionStatus.EXECUTED,
                    DecisionType.ALLOW, "Action executed.", result.data());
        }
        audit(request, action, policy, ExecutionStatus.FAILED, result.error());
        return new AgentResponse(request.requestId(), ExecutionStatus.FAILED,
                DecisionType.ALLOW, "The action failed.", null);
    }

    private AgentResponse queueForReview(AgentRequest request, AgentAction action, PolicyResponse policy) {
        reviewService.queue(action, request.user());
        audit(request, action, policy, ExecutionStatus.PENDING_REVIEW, policy.reason());
        return new AgentResponse(request.requestId(), ExecutionStatus.PENDING_REVIEW,
                DecisionType.REVIEW, "Action is waiting for human review.", null);
    }

    private AgentResponse reject(AgentRequest request, AgentAction action, PolicyResponse policy) {
        audit(request, action, policy, ExecutionStatus.REJECTED, policy.reason());
        return new AgentResponse(request.requestId(), ExecutionStatus.REJECTED,
                DecisionType.BLOCK, "Action blocked: " + policy.reason(), null);
    }

    private AgentResponse fail(AgentRequest request, AgentAction action, PolicyResponse policy, Exception e) {
        // The user gets a generic message; the real reason goes only to the audit log.
        audit(request, action, policy, ExecutionStatus.FAILED,
                e.getClass().getSimpleName() + ": " + e.getMessage());
        DecisionType decision = (policy == null) ? null : policy.decision();
        return new AgentResponse(request.requestId(), ExecutionStatus.FAILED,
                decision, "The request could not be processed.", null);
    }

    /**
     * Resolves an action that was held for human review.
     * Only the pipeline package may invoke execute() on a ToolExecutor.
     */
    public AgentResponse resolveReview(String reviewId, boolean approved, String reason, UserContext reviewer) {
        ReviewItem item = reviewService.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review item not found: " + reviewId));

        if (item.status() != ReviewStatus.PENDING) {
            throw new IllegalStateException("Review item is already resolved with status: " + item.status());
        }

        String safeReason = (reason == null || reason.isBlank()) ? "No reason provided" : reason.trim();
        String reviewerId = (reviewer == null || reviewer.userId() == null) ? "anonymous" : reviewer.userId();

        if (approved) {
            reviewService.updateStatus(reviewId, ReviewStatus.APPROVED);
            ToolExecutor executor = executors.get(item.action().agentType());
            if (executor == null) {
                throw new IllegalStateException("No executor for " + item.action().agentType());
            }
            ExecutionResult result = executor.execute(item.action());
            auditService.record(new AuditEvent(
                    UUID.randomUUID().toString(),
                    Instant.now(),
                    item.action().actionId(),
                    reviewerId,
                    item.action().agentId(),
                    item.action().operation(),
                    item.action().resource(),
                    DecisionType.ALLOW,
                    "Approved by reviewer (" + reviewerId + "): " + safeReason,
                    "HUMAN-REVIEW",
                    result.success() ? ExecutionStatus.EXECUTED : ExecutionStatus.FAILED
            ));
            return new AgentResponse(
                    item.action().actionId(),
                    result.success() ? ExecutionStatus.EXECUTED : ExecutionStatus.FAILED,
                    DecisionType.ALLOW,
                    result.success() ? "Action approved and executed." : "Action approved but execution failed: " + result.error(),
                    result.data()
            );
        } else {
            reviewService.updateStatus(reviewId, ReviewStatus.REJECTED);
            auditService.record(new AuditEvent(
                    UUID.randomUUID().toString(),
                    Instant.now(),
                    item.action().actionId(),
                    reviewerId,
                    item.action().agentId(),
                    item.action().operation(),
                    item.action().resource(),
                    DecisionType.BLOCK,
                    "Rejected by reviewer (" + reviewerId + "): " + safeReason,
                    "HUMAN-REVIEW",
                    ExecutionStatus.REJECTED
            ));
            return new AgentResponse(
                    item.action().actionId(),
                    ExecutionStatus.REJECTED,
                    DecisionType.BLOCK,
                    "Action rejected by reviewer: " + safeReason,
                    null
            );
        }
    }

    /** Writes one audit event. action and policy can be null on early failures. */
    private void audit(AgentRequest request, AgentAction action, PolicyResponse policy,
                       ExecutionStatus status, String reason) {
        auditService.record(new AuditEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                request.requestId(),
                request.user() == null ? null : request.user().userId(),
                action == null ? "UNKNOWN" : action.agentId(),
                action == null ? null : action.operation(),
                action == null ? null : action.resource(),
                policy == null ? null : policy.decision(),
                reason,
                policy == null ? null : policy.ruleId(),
                status));
    }
}
