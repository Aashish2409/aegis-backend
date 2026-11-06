package com.aegis.review;

import com.aegis.common.api.ReviewService;
import com.aegis.common.dto.AgentAction;
import com.aegis.common.dto.ReviewItem;
import com.aegis.common.dto.UserContext;
import com.aegis.common.enums.ReviewStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Holds actions that returned REVIEW. For now it only queues them.
 * Aashish adds /api/review/pending and /api/review/{id}/decide later.
 */
@Service
public class InMemoryReviewService implements ReviewService {

    private final List<ReviewItem> items = new CopyOnWriteArrayList<>();

    @Override
    public ReviewItem queue(AgentAction action, UserContext requestedBy) {
        ReviewItem item = new ReviewItem(
                UUID.randomUUID().toString(), action, requestedBy,
                ReviewStatus.PENDING, Instant.now());
        items.add(item);
        return item;
    }

    @Override
    public List<ReviewItem> findAll() {
        return List.copyOf(items);
    }
}
