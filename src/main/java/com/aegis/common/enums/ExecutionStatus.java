package com.aegis.common.enums;

/** What finally happened to a request. Stored in the audit log. */
public enum ExecutionStatus {
    EXECUTED,        // allowed and ran successfully
    REJECTED,        // blocked by policy
    PENDING_REVIEW,  // waiting for a human
    FAILED           // error (agent, LLM, executor or policy failed)
}
