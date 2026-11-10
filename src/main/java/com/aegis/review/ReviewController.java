package com.aegis.review;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aegis.common.api.CurrentUserProvider;
import com.aegis.common.api.ReviewService;
import com.aegis.common.dto.AgentResponse;
import com.aegis.common.dto.ReviewItem;
import com.aegis.common.dto.UserContext;
import com.aegis.pipeline.SecurityPipeline;

/**
 * REST controller for human reviewer workflow (owner: Aashish).
 * Only users with role REVIEWER or ADMIN may access these endpoints.
 */
@RestController
@RequestMapping("/api/review")
public class ReviewController {

    public record DecisionPayload(boolean approved, String reason) {}

    private final ReviewService reviewService;
    private final SecurityPipeline pipeline;
    private final CurrentUserProvider currentUserProvider;

    public ReviewController(ReviewService reviewService,
                            SecurityPipeline pipeline,
                            CurrentUserProvider currentUserProvider) {
        this.reviewService = reviewService;
        this.pipeline = pipeline;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/pending")
    public List<ReviewItem> pending() {
        return reviewService.findPending();
    }

    @PostMapping("/{id}/decide")
    public ResponseEntity<?> decide(@PathVariable("id") String id, @RequestBody DecisionPayload payload) {
        try {
            UserContext reviewer = currentUserProvider.getCurrentUser();
            AgentResponse response = pipeline.resolveReview(id, payload.approved(), payload.reason(), reviewer);
            HttpStatus status = payload.approved() ? HttpStatus.OK : HttpStatus.FORBIDDEN;
            return ResponseEntity.status(status).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }
}
