package com.aegis.common.dto;

import java.util.UUID;

/**
 * A request travelling through the pipeline.
 *
 * @param requestId unique id, used to link audit events to one request
 * @param message   the user's natural-language text
 * @param user      the verified caller (taken from the JWT, not from the body)
 */
public record AgentRequest(String requestId, String message, UserContext user) {

    /** Creates a request with a fresh id. */
    public static AgentRequest of(String message, UserContext user) {
        return new AgentRequest(UUID.randomUUID().toString(), message, user);
    }
}
