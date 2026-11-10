package com.aegis.email;

import com.aegis.common.api.ToolExecutor;
import com.aegis.common.dto.AgentAction;
import com.aegis.common.dto.ExecutionResult;
import com.aegis.common.enums.AgentType;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Runs approved email actions in a simulated in-memory environment.
 *
 * RULE: Only SecurityPipeline calls execute(), and only after the Policy Engine
 * returns ALLOW. Never sends real external emails.
 */
@Component
public class EmailToolExecutor implements ToolExecutor {

    private final List<SentEmail> outbox = new CopyOnWriteArrayList<>();

    @Override
    public AgentType supports() {
        return AgentType.EMAIL;
    }

    @Override
    public ExecutionResult execute(AgentAction action) {
        if (action == null || action.operation() == null || action.resource() == null) {
            return ExecutionResult.failure("Invalid action");
        }

        String resource = action.resource().trim().toLowerCase(Locale.ROOT);
        if (!"email".equals(resource) && !"emails".equals(resource)) {
            return ExecutionResult.failure("Unsupported email resource: " + action.resource());
        }

        String operation = action.operation().trim().toUpperCase(Locale.ROOT);
        return switch (operation) {
            case "SEND_EMAIL" -> send(action.parameters());
            case "READ" -> ExecutionResult.success(List.copyOf(outbox));
            case "REPORT" -> ExecutionResult.success(Map.of(
                    "totalSent", outbox.size(),
                    "latestRecipient", outbox.isEmpty() ? "none" : outbox.get(outbox.size() - 1).to()
            ));
            default -> ExecutionResult.failure("Unsupported email operation: " + operation);
        };
    }

    private ExecutionResult send(Map<String, Object> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return ExecutionResult.failure("Missing email parameters");
        }

        String to = parameters.getOrDefault("to", "").toString().trim();
        if (to.isBlank()) {
            return ExecutionResult.failure("Email recipient 'to' cannot be blank");
        }

        String subject = parameters.getOrDefault("subject", "(no subject)").toString().trim();
        String body = parameters.getOrDefault("body", "").toString().trim();

        SentEmail sentEmail = new SentEmail(
                UUID.randomUUID().toString(),
                to,
                subject,
                body,
                Instant.now()
        );

        outbox.add(sentEmail);
        return ExecutionResult.success(sentEmail);
    }

    public List<SentEmail> getOutbox() {
        return List.copyOf(outbox);
    }

    public void clear() {
        outbox.clear();
    }
}
