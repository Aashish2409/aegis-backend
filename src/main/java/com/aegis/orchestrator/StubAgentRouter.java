package com.aegis.orchestrator;

import com.aegis.common.api.Agent;
import com.aegis.common.api.AgentRouter;
import com.aegis.common.dto.AgentRequest;
import com.aegis.common.enums.AgentType;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * TEMPORARY stub (owner: Shreyoshi). Picks an agent with simple keywords.
 * Delete this file when the real Orchestrator implements AgentRouter.
 * Spring injects every Agent bean, so new agents are picked up automatically.
 */
@Service
public class StubAgentRouter implements AgentRouter {

    private final List<Agent> agents;

    public StubAgentRouter(List<Agent> agents) {
        this.agents = agents;
    }

    @Override
    public Agent route(AgentRequest request) {
        String text = request.message() == null ? "" : request.message().toLowerCase();

        AgentType wanted = AgentType.DATABASE; // default
        if (text.contains("cloud") || text.contains("file") || text.contains("vm")) {
            wanted = AgentType.CLOUD;
        } else if (text.contains("email") || text.contains("mail")) {
            wanted = AgentType.EMAIL;
        } else if (text.contains("attack") || text.contains("red team")) {
            wanted = AgentType.RED_TEAM;
        }

        final AgentType target = wanted;
        return agents.stream()
                .filter(agent -> agent.getType() == target)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No agent registered for type " + target));
    }
}
