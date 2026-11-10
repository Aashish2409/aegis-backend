package com.aegis.email;

import com.aegis.common.api.Agent;
import com.aegis.common.api.LlmClient;
import com.aegis.common.dto.AgentAction;
import com.aegis.common.dto.AgentRequest;
import com.aegis.common.enums.AgentType;
import org.springframework.stereotype.Service;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Email agent (owner: Amar).
 *
 * It only PROPOSES email actions; it NEVER executes or sends emails directly.
 * Malformed LLM output -> EmailProposalException (pipeline fails closed).
 */
@Service
public class EmailAgent implements Agent {

    public static final String AGENT_ID = "EMAIL_AGENT_01";

    private static final Pattern OPERATION_FORMAT = Pattern.compile("^[A-Z_]{1,32}$");
    private static final Pattern RESOURCE_FORMAT = Pattern.compile("^[a-z_]{1,64}$");
    private static final Set<String> ALLOWED_TOP_LEVEL_KEYS = Set.of("operation", "resource", "parameters", "params");
    private static final int MAX_PARAMS = 10;
    private static final int MAX_PARAM_LENGTH = 1000;

    private static final String SYSTEM_PROMPT = """
            You are the Email Agent of Aegis. Convert the user's request into ONE JSON object and
            output nothing else (no markdown, no explanation).
            Format: {"operation":"SEND_EMAIL|READ|REPORT","resource":"email","parameters":{"to":"user@company.test","subject":"Subject","body":"Message body"}}
            Parameters may include: to, subject, body.
            """;

    private final LlmClient llmClient;
    private final JsonMapper mapper = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    public EmailAgent(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    @Override
    public String getAgentId() {
        return AGENT_ID;
    }

    @Override
    public String getAgentName() {
        return "Email Agent";
    }

    @Override
    public AgentType getType() {
        return AgentType.EMAIL;
    }

    @Override
    public AgentAction propose(AgentRequest request) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            throw new EmailProposalException("Request or message is blank");
        }
        String raw = llmClient.complete(SYSTEM_PROMPT, request.message());
        return parse(raw);
    }

    private AgentAction parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new EmailProposalException("LLM returned an empty response");
        }

        Map<?, ?> root;
        try {
            root = mapper.readValue(raw, Map.class);
        } catch (RuntimeException e) {
            throw new EmailProposalException("LLM response is not a valid JSON object");
        }
        if (root == null) {
            throw new EmailProposalException("LLM response is null");
        }

        for (Object key : root.keySet()) {
            if (!(key instanceof String stringKey) || !ALLOWED_TOP_LEVEL_KEYS.contains(stringKey)) {
                throw new EmailProposalException("Unexpected field in LLM response: " + key);
            }
        }

        String operation = requiredString(root, "operation").trim().toUpperCase(Locale.ROOT);
        if (!OPERATION_FORMAT.matcher(operation).matches()) {
            throw new EmailProposalException("Malformed operation: " + operation);
        }

        String resource = requiredString(root, "resource").trim().toLowerCase(Locale.ROOT);
        if (!RESOURCE_FORMAT.matcher(resource).matches()) {
            throw new EmailProposalException("Malformed resource: " + resource);
        }

        Object paramsObj = root.containsKey("parameters") ? root.get("parameters") : root.get("params");
        Map<String, Object> parameters = parseParameters(paramsObj);

        return new AgentAction(
                UUID.randomUUID().toString(),
                AgentType.EMAIL,
                AGENT_ID,
                operation,
                resource,
                parameters
        );
    }

    private static String requiredString(Map<?, ?> root, String field) {
        Object value = root.get(field);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new EmailProposalException("Missing or invalid field: " + field);
        }
        return text;
    }

    private static Map<String, Object> parseParameters(Object paramsObj) {
        if (paramsObj == null) {
            return Map.of();
        }
        if (!(paramsObj instanceof Map<?, ?> map)) {
            throw new EmailProposalException("parameters must be a JSON object");
        }
        if (map.size() > MAX_PARAMS) {
            throw new EmailProposalException("Too many parameters");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key) || key.isBlank()) {
                throw new EmailProposalException("Invalid parameter key");
            }
            Object val = entry.getValue();
            if (val == null) {
                continue;
            }
            String strVal = val.toString();
            if (strVal.length() > MAX_PARAM_LENGTH) {
                throw new EmailProposalException("Parameter value too long for: " + key);
            }
            result.put(key, strVal);
        }
        return result;
    }
}
