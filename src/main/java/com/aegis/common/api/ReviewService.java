package com.aegis.common.api;

import com.aegis.common.dto.AgentAction;
import com.aegis.common.dto.ReviewItem;
import com.aegis.common.dto.UserContext;

import java.util.List;

/** Holds actions that need human approval. */
public interface ReviewService {

    ReviewItem queue(AgentAction action, UserContext requestedBy);

    List<ReviewItem> findAll();
}
