package com.aegis.policy;

import com.aegis.common.api.PolicyService;
import com.aegis.common.dto.PolicyRequest;
import com.aegis.common.dto.PolicyResponse;

import java.util.Set;

/**
 * DEPRECATED stub (owner: Amar).
 * Replaced by {@link PolicyEngine}.
 *
 * Retained without @Service to preserve backwards compatibility for existing
 * tests until test harnesses are updated to use PolicyEngine.
 */
@Deprecated
public class StubPolicyService implements PolicyService {

    private static final Set<String> ALLOWED_OPERATIONS = Set.of("READ", "SEARCH", "REPORT");

    @Override
    public PolicyResponse evaluate(PolicyRequest request) {
        String operation = request.action().operation();
        String normalized = (operation == null) ? "" : operation.trim().toUpperCase();

        if (ALLOWED_OPERATIONS.contains(normalized)) {
            return PolicyResponse.allow("Operation is on the allow-list", "STUB-ALLOWLIST");
        }
        return PolicyResponse.block("Operation not permitted: " + operation, "STUB-ALLOWLIST");
    }
}
