package com.aegis.email;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.aegis.common.api.PolicyRule;
import com.aegis.common.dto.AgentAction;
import com.aegis.common.dto.PolicyRequest;
import com.aegis.common.dto.PolicyResponse;
import com.aegis.common.enums.AgentType;

/**
 * Policy rule for Email Agent actions (owner: Amar).
 * Enforces allow-lists, review for mass mailings, and blocks unauthorized recipients.
 */
@Component
public class EmailPolicyRule implements PolicyRule {

    public static final String RULE_ID = "EMAIL-ALLOWLIST";
    private static final Set<String> ALLOWED_OPERATIONS = Set.of("READ", "SEARCH", "REPORT", "SEND_EMAIL");
    private static final Set<String> ALLOWED_RESOURCES = Set.of("emails", "inbox");
    private static final Set<String> BLOCKED_DOMAINS = Set.of("@malicious.com", "@leak.com", "@darkweb.org");

    @Override
    public boolean appliesTo(AgentAction action) {
        return action != null && action.agentType() == AgentType.EMAIL;
    }

    @Override
    public PolicyResponse check(PolicyRequest request) {
        AgentAction action = (request == null) ? null : request.action();
        if (action == null || !appliesTo(action)) {
            return PolicyResponse.block("Not an email action", RULE_ID);
        }

        String rawOperation = action.operation();
        String operation = rawOperation == null ? "" : rawOperation.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_OPERATIONS.contains(operation)) {
            return PolicyResponse.block("Email operation not permitted: " + action.operation(), RULE_ID);
        }

        String resource = action.resource() == null ? "" : action.resource().trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_RESOURCES.contains(resource)) {
            return PolicyResponse.block("Email resource not permitted: " + action.resource(), RULE_ID);
        }

        if ("SEND_EMAIL".equals(operation)) {
            Map<String, Object> params = action.parameters() == null ? Map.of() : action.parameters();
            String to = (String) params.getOrDefault("to", "");
            String toLower = to.toLowerCase(Locale.ROOT);

            for (String blockedDomain : BLOCKED_DOMAINS) {
                if (toLower.endsWith(blockedDomain)) {
                    return PolicyResponse.block("Sending email to blocked domain: " + blockedDomain, "EMAIL-DOMAIN-BLOCK");
                }
            }

            if (toLower.contains("all-company") || toLower.contains("all-employees") || toLower.contains("broadcast")) {
                return PolicyResponse.review("Company-wide broadcast email requires reviewer approval", "EMAIL-MASS-REVIEW");
            }
        }

        return PolicyResponse.allow("Email operation permitted", RULE_ID);
    }
}
