package com.aegis.common.api;

import com.aegis.common.dto.AgentRequest;

/** Decides which agent handles a request. Implemented by the Orchestrator. */
public interface AgentRouter {

    Agent route(AgentRequest request);
}
