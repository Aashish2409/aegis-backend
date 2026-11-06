package com.aegis.common.api;

import com.aegis.common.dto.AgentAction;
import com.aegis.common.dto.AgentRequest;
import com.aegis.common.enums.AgentType;

/**
 * An AI agent. It only PROPOSES an action. There is deliberately no
 * execute() method: running an action is the pipeline's job, after the
 * Policy Engine says ALLOW.
 */
public interface Agent {

    String getAgentId();

    String getAgentName();

    AgentType getType();

    /** Understand the request (usually via the LLM) and return a proposed action. */
    AgentAction propose(AgentRequest request);
}
