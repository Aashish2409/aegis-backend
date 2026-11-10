package com.aegis.email;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Component;

import com.aegis.common.api.ToolExecutor;
import com.aegis.common.dto.AgentAction;
import com.aegis.common.dto.ExecutionResult;
import com.aegis.common.enums.AgentType;

/**
 * Runs approved email actions against an in-memory simulated email store.
 * Only the pipeline package may invoke execute().
 */
@Component
public class EmailToolExecutor implements ToolExecutor {

    private final List<EmailRecord> emails = new CopyOnWriteArrayList<>();

    public EmailToolExecutor() {
        emails.add(new EmailRecord("EM001", "security@aegis.internal", "all@aegis.internal",
                "Quarterly Security Advisory", "Please review security guidelines.", Instant.now().toString()));
        emails.add(new EmailRecord("EM002", "hr@aegis.internal", "staff@aegis.internal",
                "Holiday Schedule", "Office will be closed on upcoming holidays.", Instant.now().toString()));
        emails.add(new EmailRecord("EM003", "devops@aegis.internal", "engineering@aegis.internal",
                "Deployment Window Notification", "Maintenance scheduled for Sunday 2 AM.", Instant.now().toString()));
    }

    @Override
    public AgentType supports() {
        return AgentType.EMAIL;
    }

    @Override
    public ExecutionResult execute(AgentAction action) {
        if (action == null || action.operation() == null || action.resource() == null) {
            return ExecutionResult.failure("Invalid action");
        }

        String operation = action.operation().trim().toUpperCase(Locale.ROOT);
        Map<String, Object> params = action.parameters() == null ? Map.of() : action.parameters();

        return switch (operation) {
            case "READ" -> ExecutionResult.success(List.copyOf(emails));
            case "SEARCH" -> {
                String query = (String) params.getOrDefault("query",
                        params.getOrDefault("to",
                                params.getOrDefault("subject", "")));
                if (query.isBlank()) {
                    yield ExecutionResult.success(List.copyOf(emails));
                }
                String lower = query.toLowerCase(Locale.ROOT);
                List<EmailRecord> matches = emails.stream()
                        .filter(e -> e.subject().toLowerCase(Locale.ROOT).contains(lower)
                                || e.to().toLowerCase(Locale.ROOT).contains(lower)
                                || e.body().toLowerCase(Locale.ROOT).contains(lower))
                        .toList();
                yield ExecutionResult.success(matches);
            }
            case "REPORT" -> {
                Map<String, Object> summary = Map.of(
                        "totalEmails", emails.size(),
                        "latestSubject", emails.isEmpty() ? "none" : emails.get(emails.size() - 1).subject()
                );
                yield ExecutionResult.success(summary);
            }
            case "SEND_EMAIL" -> {
                String to = (String) params.get("to");
                String subject = (String) params.getOrDefault("subject", "No Subject");
                String body = (String) params.getOrDefault("body", "");
                if (to == null || to.isBlank()) {
                    yield ExecutionResult.failure("SEND_EMAIL requires a recipient 'to'");
                }
                EmailRecord newEmail = new EmailRecord(
                        "EM-" + UUID.randomUUID().toString().substring(0, 8),
                        "system@aegis.internal",
                        to,
                        subject,
                        body,
                        Instant.now().toString()
                );
                emails.add(newEmail);
                yield ExecutionResult.success(Map.of(
                        "status", "SENT",
                        "emailId", newEmail.id(),
                        "to", newEmail.to(),
                        "subject", newEmail.subject()
                ));
            }
            default -> ExecutionResult.failure("Unsupported email operation: " + operation);
        };
    }
}
