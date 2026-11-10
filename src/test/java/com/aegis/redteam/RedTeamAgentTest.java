package com.aegis.redteam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.aegis.common.dto.AgentAction;
import com.aegis.common.dto.ExecutionResult;
import com.aegis.common.dto.PolicyRequest;
import com.aegis.common.dto.PolicyResponse;
import com.aegis.common.dto.UserContext;
import com.aegis.common.enums.AgentType;
import com.aegis.common.enums.DecisionType;
import com.aegis.common.enums.UserRole;

class RedTeamAgentTest {

    private final UserContext admin = new UserContext("admin1", UserRole.ADMIN);

    @Test
    void proposesValidProbe() {
        RedTeamAgent agent = new RedTeamAgent((s, u) -> """
                {"operation":"ATTACK_PROBE","resource":"sandbox","params":{"probeType":"INJECTION"}}
                """);
        AgentAction action = agent.propose(com.aegis.common.dto.AgentRequest.of("test probe", admin));
        assertEquals("ATTACK_PROBE", action.operation());
        assertEquals("sandbox", action.resource());
        assertEquals(AgentType.RED_TEAM, action.agentType());
    }

    @Test
    void executorRunsSandboxProbe() {
        RedTeamToolExecutor executor = new RedTeamToolExecutor();
        AgentAction action = new AgentAction(UUID.randomUUID().toString(), AgentType.RED_TEAM, "RED",
                "ATTACK_PROBE", "sandbox", Map.of());
        ExecutionResult res = executor.execute(action);
        assertTrue(res.success());
    }

    @Test
    void executorFailsOnNonSandboxTarget() {
        RedTeamToolExecutor executor = new RedTeamToolExecutor();
        AgentAction action = new AgentAction(UUID.randomUUID().toString(), AgentType.RED_TEAM, "RED",
                "ATTACK_PROBE", "prod_database", Map.of());
        ExecutionResult res = executor.execute(action);
        assertFalse(res.success());
    }

    @Test
    void policyBlocksProbingProductionResources() {
        RedTeamPolicyRule rule = new RedTeamPolicyRule();
        AgentAction action = new AgentAction(UUID.randomUUID().toString(), AgentType.RED_TEAM, "RED",
                "ATTACK_PROBE", "employees", Map.of());
        PolicyResponse response = rule.check(new PolicyRequest(action, admin));
        assertEquals(DecisionType.BLOCK, response.decision());
        assertEquals("REDTEAM-SANDBOX-GUARD", response.ruleId());
    }

    @Test
    void policyAllowsSandboxProbe() {
        RedTeamPolicyRule rule = new RedTeamPolicyRule();
        AgentAction action = new AgentAction(UUID.randomUUID().toString(), AgentType.RED_TEAM, "RED",
                "ATTACK_PROBE", "sandbox", Map.of());
        PolicyResponse response = rule.check(new PolicyRequest(action, admin));
        assertEquals(DecisionType.ALLOW, response.decision());
    }
}
