package com.aegis.database;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.aegis.common.dto.AgentAction;
import com.aegis.common.dto.PolicyRequest;
import com.aegis.common.dto.UserContext;
import com.aegis.common.enums.AgentType;
import com.aegis.common.enums.DecisionType;
import com.aegis.common.enums.UserRole;

class DatabasePolicyRuleTest {

    private final DatabasePolicyRule rule = new DatabasePolicyRule();

    private AgentAction action(AgentType type, String op, String resource) {
        return new AgentAction("a1", type, "DB_AGENT_01", op, resource, Map.of());
    }

    private DecisionType decide(AgentAction a) {
        return rule.check(new PolicyRequest(a, new UserContext("u1", UserRole.USER))).decision();
    }

    @Test
    void appliesOnlyToDatabaseActions() {
        assertTrue(rule.appliesTo(action(AgentType.DATABASE, "READ", "employees")));
        assertFalse(rule.appliesTo(action(AgentType.EMAIL, "READ", "employees")));
    }

    @Test
    void allowsOnlyReadSearchReport() {
        assertEquals(DecisionType.ALLOW, decide(action(AgentType.DATABASE, "READ", "employees")));
        assertEquals(DecisionType.ALLOW, decide(action(AgentType.DATABASE, " search ", "Employees")));
        assertEquals(DecisionType.ALLOW, decide(action(AgentType.DATABASE, "REPORT", "employees")));
    }

    @Test
    void blocksEverythingElse() {
        assertEquals(DecisionType.BLOCK, decide(action(AgentType.DATABASE, "DELETE", "employees")));
        assertEquals(DecisionType.BLOCK, decide(action(AgentType.DATABASE, "UPDATE", "employees")));
        assertEquals(DecisionType.BLOCK, decide(action(AgentType.DATABASE, null, "employees")));
        assertEquals(DecisionType.BLOCK, decide(action(AgentType.DATABASE, "READ", "salaries")));
        assertEquals(DecisionType.BLOCK, decide(action(AgentType.EMAIL, "READ", "employees")));
    }
}