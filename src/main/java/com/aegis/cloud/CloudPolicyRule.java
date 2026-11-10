package com.aegis.cloud;

import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.aegis.common.api.PolicyRule;
import com.aegis.common.dto.AgentAction;
import com.aegis.common.dto.PolicyRequest;
import com.aegis.common.dto.PolicyResponse;
import com.aegis.common.enums.AgentType;
import com.aegis.common.enums.UserRole;

/**
 * Cloud policy enforcement rule (owner: Shreyoshi).
 * Enforces allow-lists, restricts destructive VM termination, and validates resources.
 */
@Component
public class CloudPolicyRule implements PolicyRule {

    public static final String RULE_ID = "CLOUD-ALLOWLIST";
    private static final Set<String> ALLOWED_OPERATIONS = Set.of(
            "LIST_FILES", "VIEW_VM", "READ", "SEARCH", "REPORT", "UPLOAD", "DOWNLOAD", "TERMINATE_VM"
    );
    private static final Set<String> ALLOWED_RESOURCES = Set.of("cloud_resources", "vms", "files");

    @Override
    public boolean appliesTo(AgentAction action) {
        return action != null && action.agentType() == AgentType.CLOUD;
    }

    @Override
    public PolicyResponse check(PolicyRequest request) {
        AgentAction action = (request == null) ? null : request.action();
        if (action == null || !appliesTo(action)) {
            return PolicyResponse.block("Not a cloud action", RULE_ID);
        }

        String rawOperation = action.operation();
        String operation = rawOperation == null ? "" : rawOperation.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_OPERATIONS.contains(operation)) {
            return PolicyResponse.block("Cloud operation not permitted: " + action.operation(), RULE_ID);
        }

        String resource = action.resource() == null ? "" : action.resource().trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_RESOURCES.contains(resource)) {
            return PolicyResponse.block("Cloud resource not permitted: " + action.resource(), RULE_ID);
        }

        if ("TERMINATE_VM".equals(operation)) {
            UserRole role = (request.user() == null || request.user().role() == null) ? UserRole.USER : request.user().role();
            if (role != UserRole.ADMIN) {
                return PolicyResponse.block("Terminating a virtual machine is forbidden for non-admin users", "CLOUD-TERMINATE-DENY");
            }
            return PolicyResponse.review("VM termination is destructive and requires human confirmation", "CLOUD-TERMINATE-REVIEW");
        }

        return PolicyResponse.allow("Cloud operation is permitted", RULE_ID);
    }
}
