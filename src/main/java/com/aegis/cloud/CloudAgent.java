package com.aegis.cloud;

import java.util.LinkedHashMap;
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
 * Cloud agent (owner: Shreyoshi). Proposes operations on cloud infrastructure.
 * Never executes actions directly; only proposes for SecurityPipeline validation.
 */
@Service
public class CloudAgent implements Agent {

    public static final String AGENT_ID = "CLOUD_AGENT_01";

    private static final Pattern OPERATION_FORMAT = Pattern.compile("^[A-Z_]{1,32}$");
    private static final Pattern RESOURCE_FORMAT = Pattern.compile("^[a-z_]{1,64}$");
    private static final Set<String> ALLOWED_TOP_LEVEL_KEYS = Set.of("operation", "resource", "filter", "params");
    private static final int MAX_PARAMS = 10;
    private static final int MAX_VALUE_LENGTH = 200;

    private static final String SYSTEM_PROMPT = """
            You are the Cloud Agent of Aegis. Convert the user's request into ONE JSON object and
            output nothing else (no markdown, no explanation).
            Format: {"operation":"LIST_FILES|VIEW_VM|UPLOAD|DOWNLOAD|TERMINATE_VM|READ|SEARCH","resource":"cloud_resources|vms|files","params":{"name":"vm-web-01"}}
            """;

    private final LlmClient llmClient;
    private final JsonMapper mapper = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    public CloudAgent(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    @Override
    public String getAgentId() {
        return AGENT_ID;
    }

    @Override
    public String getAgentName() {
        return "Cloud Agent";
    }

    @Override
    public AgentType getType() {
        return AgentType.CLOUD;
    }

    @Override
    public AgentAction propose(AgentRequest request) {
        String raw = llmClient.complete(SYSTEM_PROMPT, request.message());
        return parse(raw);
    }

    private AgentAction parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new CloudProposalException("LLM returned an empty response");
        }

        Map<?, ?> root;
        try {
            root = mapper.readValue(raw, Map.class);
        } catch (RuntimeException e) {
            throw new CloudProposalException("LLM response is not a valid JSON object");
        }
        if (root == null) {
            throw new CloudProposalException("LLM response is not a JSON object");
        }

        for (Object key : root.keySet()) {
            if (!(key instanceof String stringKey) || !ALLOWED_TOP_LEVEL_KEYS.contains(stringKey)) {
                throw new CloudProposalException("Unexpected field in LLM response: " + key);
            }
        }

        String operation = requiredString(root, "operation").trim().toUpperCase(Locale.ROOT);
        if (!OPERATION_FORMAT.matcher(operation).matches()) {
            throw new CloudProposalException("Malformed operation");
        }

        String resource = requiredString(root, "resource").trim().toLowerCase(Locale.ROOT);
        if (!RESOURCE_FORMAT.matcher(resource).matches()) {
            throw new CloudProposalException("Malformed resource");
        }

        Object paramsObj = root.get("params") != null ? root.get("params") : root.get("filter");
        Map<String, Object> params = parseParams(paramsObj);

        return new AgentAction(UUID.randomUUID().toString(), AgentType.CLOUD, AGENT_ID,
                operation, resource, params);
    }

    private static String requiredString(Map<?, ?> root, String field) {
        Object value = root.get(field);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new CloudProposalException("Missing or invalid field: " + field);
        }
        return text;
    }

    private static Map<String, Object> parseParams(Object paramsObj) {
        if (paramsObj == null) {
            return Map.of();
        }
        if (!(paramsObj instanceof Map<?, ?> map)) {
            throw new CloudProposalException("params must be a JSON object");
        }
        if (map.size() > MAX_PARAMS) {
            throw new CloudProposalException("Too many params");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key) || key.isBlank()
                    || !(entry.getValue() instanceof String value)
                    || value.isBlank() || value.length() > MAX_VALUE_LENGTH) {
                throw new CloudProposalException("Invalid parameter entry");
            }
            result.put(key, value);
        }
        return result;
    }
}
