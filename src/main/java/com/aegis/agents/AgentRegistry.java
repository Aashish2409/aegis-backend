package com.aegis.agents;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.aegis.common.api.Agent;
import com.aegis.common.enums.AgentType;

/**
 * Agent registry (owner: Shreyoshi).
 * Keeps track of all registered agents, their metadata, permissions, and active status.
 */
@Service
public class AgentRegistry {

    private final Map<AgentType, Agent> agentsByType = new ConcurrentHashMap<>();
    private final Map<AgentType, AgentMetadata> metadataByType = new ConcurrentHashMap<>();

    public AgentRegistry(List<Agent> agents) {
        for (Agent agent : agents) {
            register(agent);
        }
    }

    public synchronized void register(Agent agent) {
        if (agent == null) {
            throw new IllegalArgumentException("Agent cannot be null");
        }
        agentsByType.put(agent.getType(), agent);
        Set<String> ops = switch (agent.getType()) {
            case DATABASE -> Set.of("READ", "SEARCH", "REPORT");
            case CLOUD -> Set.of("READ", "SEARCH", "REPORT", "LIST_FILES", "VIEW_VM", "UPLOAD", "DOWNLOAD", "TERMINATE_VM");
            case EMAIL -> Set.of("READ", "SEARCH", "REPORT", "SEND_EMAIL");
            case RED_TEAM -> Set.of("READ", "ATTACK_PROBE");
        };

        AgentMetadata metadata = new AgentMetadata(
                agent.getType(),
                agent.getAgentId(),
                agent.getAgentName(),
                "Aegis " + agent.getAgentName() + " governance integration",
                ops,
                true
        );
        metadataByType.put(agent.getType(), metadata);
    }

    public Optional<Agent> getAgent(AgentType type) {
        return Optional.ofNullable(agentsByType.get(type));
    }

    public Optional<AgentMetadata> getMetadata(AgentType type) {
        return Optional.ofNullable(metadataByType.get(type));
    }

    public Collection<AgentMetadata> listAllMetadata() {
        return Collections.unmodifiableCollection(metadataByType.values());
    }

    public boolean hasAgent(AgentType type) {
        return agentsByType.containsKey(type);
    }
}
