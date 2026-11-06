package com.aegis.common.dto;

import com.aegis.common.enums.DecisionType;

/**
 * The Policy Engine's answer.
 *
 * @param decision ALLOW, BLOCK or REVIEW
 * @param reason   human-readable reason (shown in the audit log)
 * @param ruleId   which rule produced the decision
 */
public record PolicyResponse(DecisionType decision, String reason, String ruleId) {

    public static PolicyResponse allow(String reason, String ruleId) {
        return new PolicyResponse(DecisionType.ALLOW, reason, ruleId);
    }

    public static PolicyResponse block(String reason, String ruleId) {
        return new PolicyResponse(DecisionType.BLOCK, reason, ruleId);
    }

    public static PolicyResponse review(String reason, String ruleId) {
        return new PolicyResponse(DecisionType.REVIEW, reason, ruleId);
    }
}
