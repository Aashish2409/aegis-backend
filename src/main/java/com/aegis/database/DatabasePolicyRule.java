package com.aegis.database;

import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.aegis.common.api.PolicyRule;
import com.aegis.common.dto.AgentAction;
import com.aegis.common.dto.PolicyRequest;
import com.aegis.common.dto.PolicyResponse;
import com.aegis.common.enums.AgentType;

/**
 * Database allow-list. Anything not explicitly allowed is blocked.
 * NOTE: it only takes effect once Amar's Policy Engine runs PolicyRule beans.
 */
@Component
public class DatabasePolicyRule implements PolicyRule {

    public static final String RULE_ID = "DB-ALLOWLIST";
    private static final Set<String> ALLOWED_OPERATIONS = Set.of("READ", "SEARCH", "REPORT");
    private static final Set<String> ALLOWED_RESOURCES = Set.of("employees");

    @Override
    public boolean appliesTo(AgentAction action) {
        return action != null && action.agentType() == AgentType.DATABASE;
    }

    @Override
    public PolicyResponse check(PolicyRequest request) {
        AgentAction action = (request == null) ? null : request.action();
        if (action == null || !appliesTo(action)) {
            return PolicyResponse.block("Not a database action", RULE_ID);
        }
        String rawOperation = action.operation();
        String operation = rawOperation == null ? "" : rawOperation.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_OPERATIONS.contains(operation)) {
            return PolicyResponse.block("Database operation not permitted: " + action.operation(), RULE_ID);
        }
        String resource = action.resource() == null ? "" : action.resource().trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_RESOURCES.contains(resource)) {
            return PolicyResponse.block("Database resource not permitted: " + action.resource(), RULE_ID);
        }
        return PolicyResponse.allow("Database operation is on the allow-list", RULE_ID);
    }
}