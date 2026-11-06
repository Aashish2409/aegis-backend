package com.aegis.common.api;

import com.aegis.common.dto.AgentAction;
import com.aegis.common.dto.ExecutionResult;
import com.aegis.common.enums.AgentType;

/**
 * Actually performs an approved action (reads mock data, simulates an upload,
 * simulates sending an email, ...).
 *
 * RULE: only SecurityPipeline may call execute(), and only after the Policy
 * Engine returned ALLOW (or a reviewer approved it). Do not call it from
 * agents, controllers or anywhere else.
 */
public interface ToolExecutor {

    /** Which agent type's actions this executor can run. One executor per type. */
    AgentType supports();

    ExecutionResult execute(AgentAction approvedAction);
}
