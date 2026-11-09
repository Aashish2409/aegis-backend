package com.aegis.policy;

import com.aegis.common.api.PolicyRule;
import com.aegis.common.api.PolicyService;
import com.aegis.common.dto.AgentAction;
import com.aegis.common.dto.PolicyRequest;
import com.aegis.common.dto.PolicyResponse;
import com.aegis.common.dto.UserContext;
import com.aegis.common.enums.DecisionType;
import com.aegis.common.enums.UserRole;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Enterprise Policy Engine implementing PolicyService (owner: Amar).
 *
 * Guarantees:
 * 1. Allow-list based: only explicitly allowed operations are permitted; all unknown/unexpected operations fail closed.
 * 2. Multi-rule evaluation: runs every PolicyRule where rule.appliesTo(action) is true.
 * 3. Aggregation hierarchy: Any BLOCK -> BLOCK; Else if any REVIEW -> REVIEW; Else ALLOW.
 * 4. Role-based enforcement (RBAC): enforces user role permissions from loaded YAML config.
 * 5. Prompt-injection defense: flags and blocks proposals matching adversarial injection patterns.
 */
@Service
@Primary
public class PolicyEngine implements PolicyService {

    private final List<PolicyRule> rules;
    private final PolicyConfigLoader configLoader;
    private final PromptInjectionScanner promptScanner;

    public PolicyEngine(List<PolicyRule> rules,
                        PolicyConfigLoader configLoader,
                        PromptInjectionScanner promptScanner) {
        this.rules = (rules == null) ? List.of() : List.copyOf(rules);
        this.configLoader = configLoader;
        this.promptScanner = promptScanner;
    }

    @Override
    public PolicyResponse evaluate(PolicyRequest request) {
        // Step 0: Fail-closed input validation
        if (request == null || request.action() == null) {
            return PolicyResponse.block("Invalid policy request: request or action is null", "SYS-VALIDATION");
        }

        AgentAction action = request.action();
        UserContext user = request.user();
        String operation = (action.operation() == null) ? "" : action.operation().trim().toUpperCase(Locale.ROOT);

        if (operation.isEmpty()) {
            return PolicyResponse.block("Action operation is missing or empty", "ALLOWLIST-EMPTY");
        }

        // Step 1: Prompt Injection Guard
        PromptInjectionScanner.ScanResult scanResult = promptScanner.scan(action);
        if (scanResult.flagged()) {
            return PolicyResponse.block("Prompt injection attempt detected: " + scanResult.detail(), "INJECTION-GUARD");
        }

        PolicyConfig config = configLoader.getActiveConfig();

        // Step 2: Global blocked operations (explicit override)
        if (config.globalBlockedOperations().contains(operation)) {
            return PolicyResponse.block("Operation explicitly forbidden: " + operation, "GLOBAL-BLOCKLIST");
        }

        // Step 3: Role-based Access Control (RBAC)
        UserRole userRole = (user == null || user.role() == null) ? UserRole.USER : user.role();
        Set<String> roleAllowedOps = config.allowedOperationsByRole().getOrDefault(userRole, Set.of());
        if (!roleAllowedOps.contains(operation)) {
            return PolicyResponse.block(
                    "User with role " + userRole + " is not permitted to perform operation: " + operation,
                    "RBAC-ROLE-DENY"
            );
        }

        // Step 4: Evaluate registered domain PolicyRules
        List<PolicyRule> applicableRules = rules.stream()
                .filter(rule -> rule.appliesTo(action))
                .toList();

        if (!applicableRules.isEmpty()) {
            PolicyResponse reviewResponse = null;
            for (PolicyRule rule : applicableRules) {
                PolicyResponse res = rule.check(request);
                if (res == null) {
                    return PolicyResponse.block("Policy rule returned null decision: fail closed", "RULE-NULL-SAFETY");
                }
                if (res.decision() == DecisionType.BLOCK) {
                    // Any BLOCK immediately wins
                    return res;
                }
                if (res.decision() == DecisionType.REVIEW && reviewResponse == null) {
                    reviewResponse = res;
                }
            }

            if (reviewResponse != null) {
                return reviewResponse;
            }

            return PolicyResponse.allow("All applicable policy rules allowed this action", "RULES-ALLOW");
        }

        // Step 5: Fall back to baseline Agent-level allow-list from YAML configuration
        if (config.reviewRequiredOperations().contains(operation)) {
            return PolicyResponse.review("Operation requires supervisor review", "BASELINE-REVIEW");
        }

        Set<String> agentAllowedOps = action.agentType() == null
                ? Set.of()
                : config.allowedOperationsByAgent().getOrDefault(action.agentType(), Set.of());

        if (agentAllowedOps.contains(operation)) {
            return PolicyResponse.allow("Operation permitted by agent allow-list", "BASELINE-ALLOWLIST");
        }

        // Default: Fail closed
        return PolicyResponse.block(
                "Operation " + operation + " is not permitted on resource " + action.resource(),
                "DEFAULT-DENY"
        );
    }
}
