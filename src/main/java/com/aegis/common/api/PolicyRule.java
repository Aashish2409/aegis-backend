package com.aegis.common.api;

import com.aegis.common.dto.AgentAction;
import com.aegis.common.dto.PolicyRequest;
import com.aegis.common.dto.PolicyResponse;

/**
 * One security rule. Each agent owner writes the rules for their own domain
 * (database, cloud, email). The Policy Engine runs all of them.
 */
public interface PolicyRule {

    /** True if this rule should look at the given action. */
    boolean appliesTo(AgentAction action);

    PolicyResponse check(PolicyRequest request);
}
