package com.aegis.common.dto;

import com.aegis.common.enums.DecisionType;
import com.aegis.common.enums.ExecutionStatus;

import java.time.Instant;

/**
 * One line in the audit log. Written for EVERY outcome: allowed, blocked,
 * review and failed. "decision" and "ruleId" can be null when the request
 * failed before the Policy Engine ran.
 */
public record AuditEvent(
        String eventId,
        Instant timestamp,
        String requestId,
        String userId,
        String agentId,
        String operation,
        String resource,
        DecisionType decision,
        String reason,
        String ruleId,
        ExecutionStatus status) {
}
