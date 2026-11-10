package com.aegis.cloud;

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

class CloudPolicyRuleTest {

    private CloudPolicyRule rule;
    private final UserContext normalUser = new UserContext("u1", UserRole.USER);
    private final UserContext adminUser = new UserContext("admin1", UserRole.ADMIN);

    @BeforeEach
    void setUp() {
        rule = new CloudPolicyRule();
    }

    private AgentAction action(String operation, String resource, Map<String, Object> params) {
        return new AgentAction(UUID.randomUUID().toString(), AgentType.CLOUD, CloudAgent.AGENT_ID,
                operation, resource, params);
    }

    @Test
    void allowsListFilesAndViewVm() {
        PolicyResponse res1 = rule.check(new PolicyRequest(action("LIST_FILES", "files", Map.of()), normalUser));
        assertEquals(DecisionType.ALLOW, res1.decision());

        PolicyResponse res2 = rule.check(new PolicyRequest(action("VIEW_VM", "vms", Map.of()), normalUser));
        assertEquals(DecisionType.ALLOW, res2.decision());
    }

    @Test
    void blocksTerminateVmForRegularUser() {
        PolicyResponse response = rule.check(new PolicyRequest(
                action("TERMINATE_VM", "vms", Map.of("name", "vm-web-prod")), normalUser));
        assertEquals(DecisionType.BLOCK, response.decision());
        assertEquals("CLOUD-TERMINATE-DENY", response.ruleId());
    }

    @Test
    void reviewRequiredForAdminTerminatingVm() {
        PolicyResponse response = rule.check(new PolicyRequest(
                action("TERMINATE_VM", "vms", Map.of("name", "vm-web-prod")), adminUser));
        assertEquals(DecisionType.REVIEW, response.decision());
        assertEquals("CLOUD-TERMINATE-REVIEW", response.ruleId());
    }

    @Test
    void blocksUnknownCloudOperation() {
        PolicyResponse response = rule.check(new PolicyRequest(
                action("DROP_CLUSTER", "vms", Map.of()), adminUser));
        assertEquals(DecisionType.BLOCK, response.decision());
    }

    @Test
    void appliesOnlyToCloudActions() {
        AgentAction dbAction = new AgentAction(UUID.randomUUID().toString(), AgentType.DATABASE, "DB",
                "READ", "employees", Map.of());
        assertFalse(rule.appliesTo(dbAction));
        assertTrue(rule.appliesTo(action("LIST_FILES", "files", Map.of())));
    }
}
