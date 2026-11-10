package com.aegis.common.api;

import com.aegis.common.dto.AgentAction;
import com.aegis.common.dto.ReviewItem;
import com.aegis.common.dto.UserContext;

import java.util.List;

/** Holds actions that need human approval. */
public interface ReviewService {

    ReviewItem queue(AgentAction action, UserContext requestedBy);

    List<ReviewItem> findAll();

    default List<ReviewItem> findPending() {
        return findAll().stream()
                .filter(item -> item.status() == com.aegis.common.enums.ReviewStatus.PENDING)
                .toList();
    }

    default java.util.Optional<ReviewItem> findById(String reviewId) {
        return findAll().stream()
                .filter(item -> item.reviewId().equals(reviewId))
                .findFirst();
    }

    default ReviewItem updateStatus(String reviewId, com.aegis.common.enums.ReviewStatus status) {
        throw new UnsupportedOperationException("updateStatus not implemented");
    }
}
