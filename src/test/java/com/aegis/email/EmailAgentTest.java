package com.aegis.email;

import com.aegis.common.api.LlmClient;
import com.aegis.common.dto.AgentAction;
import com.aegis.common.dto.AgentRequest;
import com.aegis.common.dto.UserContext;
import com.aegis.common.enums.AgentType;
import com.aegis.common.enums.UserRole;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmailAgentTest {

    private static final UserContext USER = new UserContext("u1", UserRole.USER);

    @Test
    void validProposalParsesCorrectly() {
        String json = """
                {
                  "operation": "SEND_EMAIL",
                  "resource": "email",
                  "parameters": {
                    "to": "bob@aegis.test",
                    "subject": "Status Report",
                    "body": "System is green."
                  }
                }
                """;
        LlmClient llm = (system, user) -> json;
        EmailAgent agent = new EmailAgent(llm);

        AgentAction action = agent.propose(AgentRequest.of("send report to bob", USER));

        assertNotNull(action);
        assertEquals(AgentType.EMAIL, action.agentType());
        assertEquals(EmailAgent.AGENT_ID, action.agentId());
        assertEquals("SEND_EMAIL", action.operation());
        assertEquals("email", action.resource());
        assertEquals("bob@aegis.test", action.parameters().get("to"));
        assertEquals("Status Report", action.parameters().get("subject"));
        assertEquals("System is green.", action.parameters().get("body"));
    }

    @Test
    void malformedJsonThrowsException() {
        LlmClient llm = (system, user) -> "{not valid json}";
        EmailAgent agent = new EmailAgent(llm);

        assertThrows(EmailProposalException.class, () ->
                agent.propose(AgentRequest.of("send email", USER)));
    }

    @Test
    void blankLlmResponseThrowsException() {
        LlmClient llm = (system, user) -> "   ";
        EmailAgent agent = new EmailAgent(llm);

        assertThrows(EmailProposalException.class, () ->
                agent.propose(AgentRequest.of("send email", USER)));
    }

    @Test
    void unexpectedFieldThrowsException() {
        String json = """
                {
                  "operation": "SEND_EMAIL",
                  "resource": "email",
                  "unexpected_admin_flag": true,
                  "parameters": {}
                }
                """;
        LlmClient llm = (system, user) -> json;
        EmailAgent agent = new EmailAgent(llm);

        assertThrows(EmailProposalException.class, () ->
                agent.propose(AgentRequest.of("send email", USER)));
    }

    @Test
    void missingOperationThrowsException() {
        String json = """
                {
                  "resource": "email",
                  "parameters": {"to": "alice@aegis.test"}
                }
                """;
        LlmClient llm = (system, user) -> json;
        EmailAgent agent = new EmailAgent(llm);

        assertThrows(EmailProposalException.class, () ->
                agent.propose(AgentRequest.of("send email", USER)));
    }
}
