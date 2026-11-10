package com.aegis.orchestrator;

import java.util.Locale;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.aegis.agents.AgentRegistry;
import com.aegis.common.api.Agent;
import com.aegis.common.api.AgentRouter;
import com.aegis.common.dto.AgentRequest;
import com.aegis.common.enums.AgentType;

/**
 * Enterprise Agent Orchestrator (owner: Shreyoshi).
 * Evaluates the user's intent and dynamically routes the request to the appropriate registered Agent.
 */
@Service
@Primary
public class Orchestrator implements AgentRouter {

    private final AgentRegistry agentRegistry;

    public Orchestrator(AgentRegistry agentRegistry) {
        this.agentRegistry = agentRegistry;
    }

    @Override
    public Agent route(AgentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("AgentRequest cannot be null");
        }

        String text = (request.message() == null) ? "" : request.message().toLowerCase(Locale.ROOT);
        AgentType target = selectAgentType(text);

        return agentRegistry.getAgent(target)
                .orElseThrow(() -> new IllegalStateException("No agent registered for type " + target));
    }

    private AgentType selectAgentType(String text) {
        if (text.contains("cloud") || text.contains("vm") || text.contains("storage")
                || text.contains("bucket") || text.contains("s3") || text.contains("server")
                || text.contains("instance")) {
            return AgentType.CLOUD;
        }

        if (text.contains("email") || text.contains("mail") || text.contains("inbox")
                || text.contains("recipient") || text.contains("subject")) {
            return AgentType.EMAIL;
        }

        if (text.contains("attack") || text.contains("red team") || text.contains("redteam")
                || text.contains("exploit") || text.contains("vulnerability") || text.contains("probe")) {
            return AgentType.RED_TEAM;
        }

        // Default agent for data queries, employees, etc.
        return AgentType.DATABASE;
    }
}
