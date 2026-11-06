package com.aegis.policy;

import com.aegis.common.api.PolicyService;
import com.aegis.common.dto.PolicyRequest;
import com.aegis.common.dto.PolicyResponse;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * TEMPORARY stub (owner: Amar). Delete this file when the real Policy Engine
 * implements PolicyService.
 *
 * It uses an ALLOW-LIST: only known-safe operations pass, everything else
 * (including an empty or unknown operation) is blocked. A deny-list ("block
 * DROP") would let anything unexpected through, so we fail closed instead.
 */
@Service
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
