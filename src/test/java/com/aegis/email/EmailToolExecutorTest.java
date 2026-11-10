package com.aegis.email;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.aegis.common.dto.AgentAction;
import com.aegis.common.dto.ExecutionResult;
import com.aegis.common.enums.AgentType;

class EmailToolExecutorTest {

    private EmailToolExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new EmailToolExecutor();
    }

    private AgentAction action(String operation, String resource, Map<String, Object> params) {
        return new AgentAction(UUID.randomUUID().toString(), AgentType.EMAIL, EmailAgent.AGENT_ID,
                operation, resource, params);
    }

    @Test
    void executeReadReturnsEmails() {
        ExecutionResult result = executor.execute(action("READ", "emails", Map.of()));
        assertTrue(result.success());
        assertTrue(result.data() instanceof List<?> list && !list.isEmpty());
    }

    @Test
    void executeSearchFiltersEmails() {
        ExecutionResult result = executor.execute(action("SEARCH", "emails", Map.of("query", "Security")));
        assertTrue(result.success());
        assertTrue(result.data() instanceof List<?> list && !list.isEmpty());
    }

    @Test
    void executeSendEmailAddsEmail() {
        ExecutionResult result = executor.execute(action("SEND_EMAIL", "emails", Map.of(
                "to", "alice@example.com",
                "subject", "Project Update",
                "body", "Meeting tomorrow at 10 AM"
        )));
        assertTrue(result.success());
        assertTrue(result.data() instanceof Map<?, ?> map && "SENT".equals(map.get("status")));
    }

    @Test
    void executeSendEmailFailsWithoutRecipient() {
        ExecutionResult result = executor.execute(action("SEND_EMAIL", "emails", Map.of()));
        assertFalse(result.success());
    }

    @Test
    void executeFailsOnUnknownOperation() {
        ExecutionResult result = executor.execute(action("DELETE_ALL", "emails", Map.of()));
        assertFalse(result.success());
    }
}
