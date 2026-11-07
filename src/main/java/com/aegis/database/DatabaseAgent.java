package com.aegis.database;

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
 * Database agent. It only PROPOSES an action; it never executes anything.
 *
 * Malformed LLM output -> DatabaseProposalException (pipeline fails closed).
 * Well-formed but unsupported operations (e.g. DELETE) are passed on as a proposal
 * so the Policy Engine BLOCKS them and the audit log records operation + resource.
 */
@Service
public class DatabaseAgent implements Agent {

    public static final String AGENT_ID = "DB_AGENT_01";

    private static final Pattern OPERATION_FORMAT = Pattern.compile("^[A-Z_]{1,32}$");
    private static final Pattern RESOURCE_FORMAT = Pattern.compile("^[a-z_]{1,64}$");
    private static final Set<String> ALLOWED_TOP_LEVEL_KEYS = Set.of("operation", "resource", "filter");
    private static final int MAX_FILTERS = 10;
    private static final int MAX_VALUE_LENGTH = 100;

    private static final String SYSTEM_PROMPT = """
            You are the Database Agent of Aegis. Convert the user's request into ONE JSON object and
            output nothing else (no markdown, no explanation).
            Format: {"operation":"READ|SEARCH|REPORT","resource":"employees","filter":{"department":"IT"}}
            Filter keys may be: id, name, department, designation. All filter values are strings.
            """;

    private final LlmClient llmClient;
    private final JsonMapper mapper = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    public DatabaseAgent(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    @Override
    public String getAgentId() {
        return AGENT_ID;
    }

    @Override
    public String getAgentName() {
        return "Database Agent";
    }

    @Override
    public AgentType getType() {
        return AgentType.DATABASE;
    }

    @Override
    public AgentAction propose(AgentRequest request) {
        String raw = llmClient.complete(SYSTEM_PROMPT, request.message());
        return parse(raw);
    }

    private AgentAction parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new DatabaseProposalException("LLM returned an empty response");
        }

        Map<?, ?> root;
        try {
            root = mapper.readValue(raw, Map.class);
        } catch (RuntimeException e) {
            throw new DatabaseProposalException("LLM response is not a valid JSON object");
        }
        if (root == null) {
            throw new DatabaseProposalException("LLM response is not a JSON object");
        }

        for (Object key : root.keySet()) {
            if (!(key instanceof String stringKey) || !ALLOWED_TOP_LEVEL_KEYS.contains(stringKey)) {
                throw new DatabaseProposalException("Unexpected field in LLM response: " + key);
            }
        }

        String operation = requiredString(root, "operation").trim().toUpperCase(Locale.ROOT);
        if (!OPERATION_FORMAT.matcher(operation).matches()) {
            throw new DatabaseProposalException("Malformed operation");
        }

        String resource = requiredString(root, "resource").trim().toLowerCase(Locale.ROOT);
        if (!RESOURCE_FORMAT.matcher(resource).matches()) {
            throw new DatabaseProposalException("Malformed resource");
        }

        return new AgentAction(UUID.randomUUID().toString(), AgentType.DATABASE, AGENT_ID,
                operation, resource, parseFilter(root.get("filter")));
    }

    private static String requiredString(Map<?, ?> root, String field) {
        Object value = root.get(field);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new DatabaseProposalException("Missing or invalid field: " + field);
        }
        return text;
    }

    private static Map<String, Object> parseFilter(Object filter) {
        if (filter == null) {
            return Map.of();
        }
        if (!(filter instanceof Map<?, ?> map)) {
            throw new DatabaseProposalException("filter must be a JSON object");
        }
        if (map.size() > MAX_FILTERS) {
            throw new DatabaseProposalException("Too many filters");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key) || key.isBlank()
                    || !(entry.getValue() instanceof String value)
                    || value.isBlank() || value.length() > MAX_VALUE_LENGTH) {
                throw new DatabaseProposalException("Invalid filter entry");
            }
            result.put(key, value);
        }
        return result;
    }
}