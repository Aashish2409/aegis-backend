package com.aegis.agents;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.aegis.common.api.Agent;
import com.aegis.common.dto.AgentAction;
import com.aegis.common.dto.AgentRequest;
import com.aegis.common.enums.AgentType;

class AgentRegistryTest {

    private static class DummyAgent implements Agent {
        private final AgentType type;

        DummyAgent(AgentType type) { this.type = type; }
        @Override public String getAgentId() { return "DUMMY_" + type; }
        @Override public String getAgentName() { return "Dummy " + type; }
        @Override public AgentType getType() { return type; }
        @Override public AgentAction propose(AgentRequest request) {
            return new AgentAction(UUID.randomUUID().toString(), type, getAgentId(), "READ", "test", Map.of());
        }
    }

    @Test
    void registersAndFindsAgents() {
        DummyAgent db = new DummyAgent(AgentType.DATABASE);
        DummyAgent cloud = new DummyAgent(AgentType.CLOUD);

        AgentRegistry registry = new AgentRegistry(List.of(db, cloud));

        assertTrue(registry.hasAgent(AgentType.DATABASE));
        assertTrue(registry.hasAgent(AgentType.CLOUD));
        assertFalse(registry.hasAgent(AgentType.EMAIL));

        assertEquals("DUMMY_DATABASE", registry.getAgent(AgentType.DATABASE).orElseThrow().getAgentId());
        assertEquals("DUMMY_CLOUD", registry.getMetadata(AgentType.CLOUD).orElseThrow().agentId());
        assertEquals(2, registry.listAllMetadata().size());
    }
}
