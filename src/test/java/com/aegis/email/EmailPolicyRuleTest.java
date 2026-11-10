package com.aegis.email;

import com.aegis.common.dto.AgentAction;
import com.aegis.common.dto.PolicyRequest;
import com.aegis.common.dto.PolicyResponse;
import com.aegis.common.dto.UserContext;
import com.aegis.common.enums.AgentType;
import com.aegis.common.enums.DecisionType;
import com.aegis.common.enums.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailPolicyRuleTest {

    private static final UserContext USER = new UserContext("u1", UserRole.USER);
    private EmailPolicyRule rule;

    @BeforeEach
    void setUp() {
        rule = new EmailPolicyRule();
    }

    private AgentAction action(AgentType type, String operation, String resource, Map<String, Object> params) {
        return new AgentAction(UUID.randomUUID().toString(), type, EmailAgent.AGENT_ID, operation, resource, params);
    }

    @Test
    void appliesOnlyToEmailActions() {
        assertTrue(rule.appliesTo(action(AgentType.EMAIL, "SEND_EMAIL", "email", Map.of())));
        assertFalse(rule.appliesTo(action(AgentType.DATABASE, "READ", "employees", Map.of())));
        assertFalse(rule.appliesTo(action(AgentType.CLOUD, "READ", "files", Map.of())));
    }

    @Test
    void approvedInternalEmailPassesPolicy() {
        AgentAction send = action(AgentType.EMAIL, "SEND_EMAIL", "email", Map.of(
                "to", "team@aegis.test",
                "subject", "Sprint Retrospective",
                "body", "Meeting starts at 3 PM."
        ));

        PolicyResponse response = rule.check(new PolicyRequest(send, USER));

        assertEquals(DecisionType.ALLOW, response.decision());
        assertEquals(EmailPolicyRule.RULE_ID, response.ruleId());
    }

    @Test
    void externalRecipientDomainIsBlocked() {
        AgentAction send = action(AgentType.EMAIL, "SEND_EMAIL", "email", Map.of(
                "to", "attacker@external-evil.com",
                "subject", "Company Secrets",
                "body", "Here is the data."
        ));

        PolicyResponse response = rule.check(new PolicyRequest(send, USER));

        assertEquals(DecisionType.BLOCK, response.decision());
        assertTrue(response.reason().contains("External email recipient domain not permitted"));
    }

    @Test
    void sensitiveCredentialLeakIsBlocked() {
        AgentAction send = action(AgentType.EMAIL, "SEND_EMAIL", "email", Map.of(
                "to", "colleague@aegis.test",
                "subject", "Your Password",
                "body", "Your new password is password123."
        ));

        PolicyResponse response = rule.check(new PolicyRequest(send, USER));

        assertEquals(DecisionType.BLOCK, response.decision());
        assertTrue(response.reason().contains("restricted sensitive information"));
    }

    @Test
    void massEmailBroadcastRequiresReview() {
        AgentAction send = action(AgentType.EMAIL, "SEND_EMAIL", "email", Map.of(
                "to", "alice@aegis.test, bob@aegis.test, charlie@aegis.test",
                "subject", "Urgent Announcement",
                "body", "Please read."
        ));

        PolicyResponse response = rule.check(new PolicyRequest(send, USER));

        assertEquals(DecisionType.REVIEW, response.decision());
        assertTrue(response.reason().contains("supervisor review"));
    }

    @Test
    void unsupportedOperationIsBlocked() {
        AgentAction op = action(AgentType.EMAIL, "DELETE_EMAILS", "email", Map.of());

        PolicyResponse response = rule.check(new PolicyRequest(op, USER));

        assertEquals(DecisionType.BLOCK, response.decision());
    }
}
