package com.aegis.email;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.aegis.common.dto.AgentAction;
import com.aegis.common.dto.PolicyRequest;
import com.aegis.common.dto.PolicyResponse;
import com.aegis.common.dto.UserContext;
import com.aegis.common.enums.AgentType;
import com.aegis.common.enums.DecisionType;
import com.aegis.common.enums.UserRole;

class EmailPolicyRuleTest {

    private EmailPolicyRule rule;
    private final UserContext user = new UserContext("u1", UserRole.USER);

    @BeforeEach
    void setUp() {
        rule = new EmailPolicyRule();
    }

    private AgentAction action(String operation, String resource, Map<String, Object> params) {
        return new AgentAction(UUID.randomUUID().toString(), AgentType.EMAIL, EmailAgent.AGENT_ID,
                operation, resource, params);
    }

    @Test
    void allowsNormalReadAndSearch() {
        PolicyResponse response = rule.check(new PolicyRequest(action("READ", "emails", Map.of()), user));
        assertEquals(DecisionType.ALLOW, response.decision());

        PolicyResponse searchRes = rule.check(new PolicyRequest(action("SEARCH", "emails", Map.of("query", "test")), user));
        assertEquals(DecisionType.ALLOW, searchRes.decision());
    }

    @Test
    void allowsNormalSendEmail() {
        PolicyResponse response = rule.check(new PolicyRequest(
                action("SEND_EMAIL", "emails", Map.of("to", "colleague@aegis.internal")), user));
        assertEquals(DecisionType.ALLOW, response.decision());
    }

    @Test
    void blocksSendEmailToMaliciousDomain() {
        PolicyResponse response = rule.check(new PolicyRequest(
                action("SEND_EMAIL", "emails", Map.of("to", "attacker@malicious.com")), user));
        assertEquals(DecisionType.BLOCK, response.decision());
        assertEquals("EMAIL-DOMAIN-BLOCK", response.ruleId());
    }

    @Test
    void reviewsBroadcastEmail() {
        PolicyResponse response = rule.check(new PolicyRequest(
                action("SEND_EMAIL", "emails", Map.of("to", "all-company@aegis.internal")), user));
        assertEquals(DecisionType.REVIEW, response.decision());
        assertEquals("EMAIL-MASS-REVIEW", response.ruleId());
    }

    @Test
    void blocksUnknownOperation() {
        PolicyResponse response = rule.check(new PolicyRequest(
                action("PURGE_INBOX", "emails", Map.of()), user));
        assertEquals(DecisionType.BLOCK, response.decision());
    }

    @Test
    void appliesOnlyToEmailActions() {
        AgentAction dbAction = new AgentAction(UUID.randomUUID().toString(), AgentType.DATABASE, "DB",
                "READ", "employees", Map.of());
        assertFalse(rule.appliesTo(dbAction));
        assertTrue(rule.appliesTo(action("READ", "emails", Map.of())));
    }
}
