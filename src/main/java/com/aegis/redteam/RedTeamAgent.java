package com.aegis.redteam;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.aegis.common.api.Agent;
import com.aegis.common.api.LlmClient;
import com.aegis.common.dto.AgentAction;
import com.aegis.common.dto.AgentRequest;
import com.aegis.common.enums.AgentType;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Red Team security agent (owner: Aashish).
 * Generates and proposes security probing actions against Aegis test environments.
 */
@Service
public class RedTeamAgent implements Agent {

    public static final String AGENT_ID = "RED_TEAM_AGENT_01";

    private static final Pattern OPERATION_FORMAT = Pattern.compile("^[A-Z_]{1,32}$");
    private static final Pattern RESOURCE_FORMAT = Pattern.compile("^[a-z_]{1,64}$");
    private static final Set<String> ALLOWED_TOP_LEVEL_KEYS = Set.of("operation", "resource", "filter", "params");

    private static final String SYSTEM_PROMPT = """
            You are the Red Team Agent of Aegis. Convert the request into ONE JSON object and
            output nothing else (no markdown, no explanation).
            Format: {"operation":"ATTACK_PROBE|READ","resource":"sandbox|testbed","params":{"probeType":"INJECTION"}}
            """;

    private final LlmClient llmClient;
    private final JsonMapper mapper = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    public RedTeamAgent(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    @Override
    public String getAgentId() {
        return AGENT_ID;
    }

    @Override
    public String getAgentName() {
        return "Red Team Agent";
    }

    @Override
    public AgentType getType() {
        return AgentType.RED_TEAM;
    }

    @Override
    public AgentAction propose(AgentRequest request) {
        String raw = llmClient.complete(SYSTEM_PROMPT, request.message());
        return parse(raw);
    }

    private AgentAction parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("LLM returned an empty response");
        }

        Map<?, ?> root;
        try {
            root = mapper.readValue(raw, Map.class);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("LLM response is not a valid JSON object");
        }

        for (Object key : root.keySet()) {
            if (!(key instanceof String stringKey) || !ALLOWED_TOP_LEVEL_KEYS.contains(stringKey)) {
                throw new IllegalArgumentException("Unexpected field in LLM response: " + key);
            }
        }

        String operation = ((String) root.get("operation")).trim().toUpperCase(Locale.ROOT);
        if (!OPERATION_FORMAT.matcher(operation).matches()) {
            throw new IllegalArgumentException("Malformed operation");
        }

        String resource = ((String) root.get("resource")).trim().toLowerCase(Locale.ROOT);
        if (!RESOURCE_FORMAT.matcher(resource).matches()) {
            throw new IllegalArgumentException("Malformed resource");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) (root.get("params") != null ? root.get("params") : Map.of());

        return new AgentAction(UUID.randomUUID().toString(), AgentType.RED_TEAM, AGENT_ID,
                operation, resource, params);
    }
}
