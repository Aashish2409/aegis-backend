package com.aegis.orchestrator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.aegis.agents.AgentRegistry;
import com.aegis.common.api.Agent;
import com.aegis.common.dto.AgentAction;
import com.aegis.common.dto.AgentRequest;
import com.aegis.common.dto.UserContext;
import com.aegis.common.enums.AgentType;
import com.aegis.common.enums.UserRole;

class OrchestratorTest {

    private Orchestrator orchestrator;
    private final UserContext user = new UserContext("u1", UserRole.USER);

    private static class DummyAgent implements Agent {
        private final AgentType type;

        DummyAgent(AgentType type) { this.type = type; }
        @Override public String getAgentId() { return "AGENT_" + type; }
        @Override public String getAgentName() { return "Agent " + type; }
        @Override public AgentType getType() { return type; }
        @Override public AgentAction propose(AgentRequest request) {
            return new AgentAction(UUID.randomUUID().toString(), type, getAgentId(), "READ", "test", Map.of());
        }
    }

    @BeforeEach
    void setUp() {
        AgentRegistry registry = new AgentRegistry(List.of(
                new DummyAgent(AgentType.DATABASE),
                new DummyAgent(AgentType.CLOUD),
                new DummyAgent(AgentType.EMAIL),
                new DummyAgent(AgentType.RED_TEAM)
        ));
        orchestrator = new Orchestrator(registry);
    }

    @Test
    void routesDatabaseQueriesByDefault() {
        Agent agent = orchestrator.route(AgentRequest.of("show all IT employees", user));
        assertEquals(AgentType.DATABASE, agent.getType());
    }

    @Test
    void routesCloudRequests() {
        Agent agent = orchestrator.route(AgentRequest.of("list all files in cloud storage vm", user));
        assertEquals(AgentType.CLOUD, agent.getType());
    }

    @Test
    void routesEmailRequests() {
        Agent agent = orchestrator.route(AgentRequest.of("send an email to team", user));
        assertEquals(AgentType.EMAIL, agent.getType());
    }

    @Test
    void routesRedTeamRequests() {
        Agent agent = orchestrator.route(AgentRequest.of("run red team vulnerability probe", user));
        assertEquals(AgentType.RED_TEAM, agent.getType());
    }

    @Test
    void throwsExceptionWhenNoAgentRegistered() {
        AgentRegistry emptyRegistry = new AgentRegistry(List.of());
        Orchestrator emptyOrchestrator = new Orchestrator(emptyRegistry);

        assertThrows(IllegalStateException.class, () ->
                emptyOrchestrator.route(AgentRequest.of("show employees", user)));
    }
}
