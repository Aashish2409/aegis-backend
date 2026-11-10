package com.aegis.agents;

import java.util.Set;

import com.aegis.common.enums.AgentType;

/**
 * Metadata descriptor for an agent registered in Aegis.
 */
public record AgentMetadata(
        AgentType type,
        String agentId,
        String name,
        String description,
        Set<String> supportedOperations,
        boolean active
) {}
