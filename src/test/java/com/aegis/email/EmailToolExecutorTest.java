package com.aegis.email;

import com.aegis.common.dto.AgentAction;
import com.aegis.common.dto.ExecutionResult;
import com.aegis.common.enums.AgentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailToolExecutorTest {

    private EmailToolExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new EmailToolExecutor();
    }

    private AgentAction action(String operation, String resource, Map<String, Object> params) {
        return new AgentAction(UUID.randomUUID().toString(), AgentType.EMAIL, EmailAgent.AGENT_ID, operation, resource, params);
    }

    @Test
    void supportsEmailAgentType() {
        assertEquals(AgentType.EMAIL, executor.supports());
    }

    @Test
    void sendEmailAppendsToOutboxAndSucceeds() {
        AgentAction send = action("SEND_EMAIL", "email", Map.of(
                "to", "alice@company.test",
                "subject", "Project Update",
                "body", "All tasks complete."
        ));

        ExecutionResult result = executor.execute(send);

        assertTrue(result.success());
        assertNotNull(result.data());
        assertTrue(result.data() instanceof SentEmail);

        SentEmail sent = (SentEmail) result.data();
        assertEquals("alice@company.test", sent.to());
        assertEquals("Project Update", sent.subject());
        assertEquals(1, executor.getOutbox().size());
    }

    @Test
    void readOperationReturnsOutboxList() {
        executor.execute(action("SEND_EMAIL", "email", Map.of("to", "a@aegis.test", "subject", "A", "body", "1")));
        executor.execute(action("SEND_EMAIL", "email", Map.of("to", "b@aegis.test", "subject", "B", "body", "2")));

        ExecutionResult result = executor.execute(action("READ", "email", Map.of()));

        assertTrue(result.success());
        List<?> list = (List<?>) result.data();
        assertEquals(2, list.size());
    }

    @Test
    void unsupportedOperationFails() {
        ExecutionResult result = executor.execute(action("DELETE_SERVER", "email", Map.of()));
        assertFalse(result.success());
    }

    @Test
    void unsupportedResourceFails() {
        ExecutionResult result = executor.execute(action("SEND_EMAIL", "cloud_vm", Map.of("to", "x@aegis.test")));
        assertFalse(result.success());
    }
}
