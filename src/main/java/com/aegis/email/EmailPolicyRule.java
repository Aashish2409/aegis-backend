package com.aegis.email;

import com.aegis.common.api.PolicyRule;
import com.aegis.common.dto.AgentAction;
import com.aegis.common.dto.PolicyRequest;
import com.aegis.common.dto.PolicyResponse;
import com.aegis.common.enums.AgentType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Policy rule governing all email operations (owner: Amar).
 *
 * Enforces:
 * 1. Allow-listed operations and resources.
 * 2. Data exfiltration defense: blocks sending emails to unapproved external domains.
 * 3. Sensitive data leak prevention: blocks emails containing passwords, API keys, or credentials.
 * 4. Mass broadcast guard: flags multi-recipient or broadcast emails for human REVIEW.
 */
@Component
public class EmailPolicyRule implements PolicyRule {

    public static final String RULE_ID = "EMAIL-POLICY-RULE";

    private static final Set<String> ALLOWED_OPERATIONS = Set.of("SEND_EMAIL", "READ", "REPORT");
    private static final Set<String> ALLOWED_RESOURCES = Set.of("email", "emails");

    private static final Set<String> ALLOWED_DOMAINS = Set.of(
            "aegis.com",
            "aegis.test",
            "company.com",
            "company.test"
    );

    private static final List<String> SENSITIVE_KEYWORDS = List.of(
            "password",
            "api_key",
            "apikey",
            "secret_key",
            "private_key",
            "credit_card",
            "social_security",
            "ssn"
    );

    @Override
    public boolean appliesTo(AgentAction action) {
        return action != null && action.agentType() == AgentType.EMAIL;
    }

    @Override
    public PolicyResponse check(PolicyRequest request) {
        AgentAction action = (request == null) ? null : request.action();
        if (action == null || !appliesTo(action)) {
            return PolicyResponse.block("Not an email action", RULE_ID);
        }

        String operation = action.operation() == null ? "" : action.operation().trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_OPERATIONS.contains(operation)) {
            return PolicyResponse.block("Email operation not permitted: " + action.operation(), RULE_ID);
        }

        String resource = action.resource() == null ? "" : action.resource().trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_RESOURCES.contains(resource)) {
            return PolicyResponse.block("Email resource not permitted: " + action.resource(), RULE_ID);
        }

        if ("SEND_EMAIL".equals(operation)) {
            return checkSendEmail(action.parameters());
        }

        return PolicyResponse.allow("Email operation permitted by policy", RULE_ID);
    }

    private PolicyResponse checkSendEmail(Map<String, Object> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return PolicyResponse.block("Missing required parameters for SEND_EMAIL", RULE_ID);
        }

        String to = parameters.getOrDefault("to", "").toString().trim().toLowerCase(Locale.ROOT);
        if (to.isBlank()) {
            return PolicyResponse.block("Recipient 'to' address cannot be empty", RULE_ID);
        }

        // Check for mass broadcast
        if (to.contains(",") || to.contains(";") || to.contains("all@") || to.contains("everyone@")) {
            return PolicyResponse.review("Mass email broadcast requires supervisor review", RULE_ID);
        }

        // Validate domain (Exfiltration Protection)
        int atIndex = to.lastIndexOf('@');
        if (atIndex < 0 || atIndex == to.length() - 1) {
            return PolicyResponse.block("Invalid email address format: " + to, RULE_ID);
        }

        String domain = to.substring(atIndex + 1).trim();
        if (!ALLOWED_DOMAINS.contains(domain)) {
            return PolicyResponse.block(
                    "External email recipient domain not permitted for automated sending: @" + domain,
                    RULE_ID
            );
        }

        // Check for sensitive content leakage in subject and body
        String subject = parameters.getOrDefault("subject", "").toString().toLowerCase(Locale.ROOT);
        String body = parameters.getOrDefault("body", "").toString().toLowerCase(Locale.ROOT);
        String fullContent = subject + " " + body;

        for (String keyword : SENSITIVE_KEYWORDS) {
            if (fullContent.contains(keyword)) {
                return PolicyResponse.block(
                        "Email content contains restricted sensitive information (" + keyword + ")",
                        RULE_ID
                );
            }
        }

        return PolicyResponse.allow("Email operation permitted by policy", RULE_ID);
    }
}
