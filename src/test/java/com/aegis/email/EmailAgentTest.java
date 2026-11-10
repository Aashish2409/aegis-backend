package com.aegis.email;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.aegis.common.api.LlmClient;
import com.aegis.common.dto.AgentAction;
import com.aegis.common.dto.AgentRequest;
import com.aegis.common.dto.UserContext;
import com.aegis.common.enums.AgentType;
import com.aegis.common.enums.UserRole;

class EmailAgentTest {

    private final UserContext user = new UserContext("u1", UserRole.USER);

    @Test
    void proposesValidSendEmailAction() {
        LlmClient mockLlm = (system, userMsg) -> """
                {"operation":"SEND_EMAIL","resource":"emails","params":{"to":"bob@aegis.internal","subject":"Hello","body":"Work update"}}
                """;
        EmailAgent agent = new EmailAgent(mockLlm);
        AgentAction action = agent.propose(AgentRequest.of("send an email to bob", user));

        assertEquals("SEND_EMAIL", action.operation());
        assertEquals("emails", action.resource());
        assertEquals(AgentType.EMAIL, action.agentType());
        assertEquals("bob@aegis.internal", action.parameters().get("to"));
        assertEquals("Hello", action.parameters().get("subject"));
    }

    @Test
    void proposesValidReadAction() {
        LlmClient mockLlm = (system, userMsg) -> """
                {"operation":"READ","resource":"emails","params":{}}
                """;
        EmailAgent agent = new EmailAgent(mockLlm);
        AgentAction action = agent.propose(AgentRequest.of("read my emails", user));

        assertEquals("READ", action.operation());
        assertEquals("emails", action.resource());
    }

    @Test
    void malformedLlmResponseThrowsProposalException() {
        LlmClient mockLlm = (system, userMsg) -> "not json at all";
        EmailAgent agent = new EmailAgent(mockLlm);

        assertThrows(EmailProposalException.class, () -> agent.propose(AgentRequest.of("hello", user)));
    }

    @Test
    void emptyLlmResponseThrowsProposalException() {
        LlmClient mockLlm = (system, userMsg) -> "";
        EmailAgent agent = new EmailAgent(mockLlm);

        assertThrows(EmailProposalException.class, () -> agent.propose(AgentRequest.of("hello", user)));
    }
}
