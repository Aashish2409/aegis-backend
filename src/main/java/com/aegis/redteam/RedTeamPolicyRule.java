package com.aegis.redteam;

import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.aegis.common.api.PolicyRule;
import com.aegis.common.dto.AgentAction;
import com.aegis.common.dto.PolicyRequest;
import com.aegis.common.dto.PolicyResponse;
import com.aegis.common.enums.AgentType;

/**
 * Policy guard for Red Team probing actions (owner: Aashish).
 * Ensures red-team simulations cannot target production resources.
 */
@Component
public class RedTeamPolicyRule implements PolicyRule {

    public static final String RULE_ID = "REDTEAM-SANDBOX-GUARD";
    private static final Set<String> ALLOWED_RESOURCES = Set.of("sandbox", "testbed");

    @Override
    public boolean appliesTo(AgentAction action) {
        return action != null && action.agentType() == AgentType.RED_TEAM;
    }

    @Override
    public PolicyResponse check(PolicyRequest request) {
        AgentAction action = (request == null) ? null : request.action();
        if (action == null || !appliesTo(action)) {
            return PolicyResponse.block("Not a red team action", RULE_ID);
        }

        String resource = action.resource() == null ? "" : action.resource().trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_RESOURCES.contains(resource)) {
            return PolicyResponse.block("Red team probes are restricted strictly to test sandboxes: " + action.resource(), RULE_ID);
        }

        return PolicyResponse.allow("Red team probe approved for sandbox", RULE_ID);
    }
}
