package com.aegis.common.dto;

import com.aegis.common.enums.AgentType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An action an agent PROPOSES. Agents never run it themselves; the pipeline
 * checks it with the Policy Engine first.
 *
 * Example: operation=READ, resource=employees, parameters={department=IT}
 *
 * @param actionId   unique id of this proposal
 * @param agentType  which kind of agent proposed it
 * @param agentId    the specific agent, for example "DB_AGENT_01"
 * @param operation  what to do (READ, SEARCH, REPORT, DELETE, ...)
 * @param resource   what to do it on (employees, a cloud file, an email, ...)
 * @param parameters extra details such as filters
 */
public record AgentAction(
        String actionId,
        AgentType agentType,
        String agentId,
        String operation,
        String resource,
        Map<String, Object> parameters) {

    /** Makes parameters never null and read-only. */
    public AgentAction {
        parameters = (parameters == null)
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
    }
}
