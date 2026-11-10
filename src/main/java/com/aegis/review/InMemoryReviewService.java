package com.aegis.review;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.aegis.common.api.ReviewService;
import com.aegis.common.dto.AgentAction;
import com.aegis.common.dto.ReviewItem;
import com.aegis.common.dto.UserContext;
import com.aegis.common.enums.ReviewStatus;

/**
 * Holds actions that returned REVIEW (owner: Aashish).
 * Supports queueing, listing pending items, and recording review decisions.
 */
@Service
public class InMemoryReviewService implements ReviewService {

    private final Map<String, ReviewItem> items = new ConcurrentHashMap<>();

    @Override
    public ReviewItem queue(AgentAction action, UserContext requestedBy) {
        ReviewItem item = new ReviewItem(
                UUID.randomUUID().toString(), action, requestedBy,
                ReviewStatus.PENDING, Instant.now());
        items.put(item.reviewId(), item);
        return item;
    }

    @Override
    public List<ReviewItem> findAll() {
        return List.copyOf(items.values());
    }

    @Override
    public List<ReviewItem> findPending() {
        return items.values().stream()
                .filter(item -> item.status() == ReviewStatus.PENDING)
                .toList();
    }

    @Override
    public Optional<ReviewItem> findById(String reviewId) {
        return Optional.ofNullable(items.get(reviewId));
    }

    @Override
    public ReviewItem updateStatus(String reviewId, ReviewStatus status) {
        ReviewItem existing = items.get(reviewId);
        if (existing == null) {
            throw new IllegalArgumentException("Review item not found: " + reviewId);
        }
        ReviewItem updated = new ReviewItem(
                existing.reviewId(),
                existing.action(),
                existing.requestedBy(),
                status,
                existing.createdAt()
        );
        items.put(reviewId, updated);
        return updated;
    }
}
