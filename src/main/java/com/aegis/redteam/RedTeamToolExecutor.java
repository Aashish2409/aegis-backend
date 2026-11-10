package com.aegis.redteam;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.aegis.common.api.ToolExecutor;
import com.aegis.common.dto.AgentAction;
import com.aegis.common.dto.ExecutionResult;
import com.aegis.common.enums.AgentType;

/**
 * Runs approved Red Team security probes against mock sandbox environments.
 * Only SecurityPipeline may call execute().
 */
@Component
public class RedTeamToolExecutor implements ToolExecutor {

    @Override
    public AgentType supports() {
        return AgentType.RED_TEAM;
    }

    @Override
    public ExecutionResult execute(AgentAction action) {
        if (action == null || action.operation() == null || action.resource() == null) {
            return ExecutionResult.failure("Invalid action");
        }

        if (!"sandbox".equalsIgnoreCase(action.resource()) && !"testbed".equalsIgnoreCase(action.resource())) {
            return ExecutionResult.failure("Red team executor only allows executing against 'sandbox' or 'testbed'");
        }

        return switch (action.operation().toUpperCase()) {
            case "ATTACK_PROBE", "READ" -> ExecutionResult.success(Map.of(
                    "status", "PROBE_COMPLETED",
                    "target", action.resource(),
                    "findings", "No policy leakage detected in sandbox environment"
            ));
            default -> ExecutionResult.failure("Unsupported red team operation: " + action.operation());
        };
    }
}
