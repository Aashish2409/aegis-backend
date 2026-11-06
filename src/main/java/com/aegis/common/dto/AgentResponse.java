package com.aegis.common.dto;

import com.aegis.common.enums.DecisionType;
import com.aegis.common.enums.ExecutionStatus;

/**
 * The final answer returned to the user.
 *
 * @param decision null when the request failed before the policy check
 * @param data     the result data (for example employee records), if any
 */
public record AgentResponse(
        String requestId,
        ExecutionStatus status,
        DecisionType decision,
        String message,
        Object data) {
}
