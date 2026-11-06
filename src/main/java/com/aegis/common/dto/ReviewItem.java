package com.aegis.common.dto;

import com.aegis.common.enums.ReviewStatus;

import java.time.Instant;

/** An action held for human approval after a REVIEW decision. */
public record ReviewItem(
        String reviewId,
        AgentAction action,
        UserContext requestedBy,
        ReviewStatus status,
        Instant createdAt) {
}
