package com.aegis.cloud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.aegis.common.api.LlmClient;
import com.aegis.common.dto.AgentAction;
import com.aegis.common.dto.AgentRequest;
import com.aegis.common.dto.UserContext;
import com.aegis.common.enums.AgentType;
import com.aegis.common.enums.UserRole;

class CloudAgentTest {

    private final UserContext user = new UserContext("u1", UserRole.USER);

    @Test
    void proposesValidListFilesAction() {
        LlmClient mockLlm = (system, userMsg) -> """
                {"operation":"LIST_FILES","resource":"files","params":{}}
                """;
        CloudAgent agent = new CloudAgent(mockLlm);
        AgentAction action = agent.propose(AgentRequest.of("list all backup files", user));

        assertEquals("LIST_FILES", action.operation());
        assertEquals("files", action.resource());
        assertEquals(AgentType.CLOUD, action.agentType());
    }

    @Test
    void proposesValidViewVmAction() {
        LlmClient mockLlm = (system, userMsg) -> """
                {"operation":"VIEW_VM","resource":"vms","params":{"name":"vm-web-prod"}}
                """;
        CloudAgent agent = new CloudAgent(mockLlm);
        AgentAction action = agent.propose(AgentRequest.of("check status of vm-web-prod", user));

        assertEquals("VIEW_VM", action.operation());
        assertEquals("vms", action.resource());
        assertEquals("vm-web-prod", action.parameters().get("name"));
    }

    @Test
    void malformedLlmResponseThrowsProposalException() {
        LlmClient mockLlm = (system, userMsg) -> "not json";
        CloudAgent agent = new CloudAgent(mockLlm);

        assertThrows(CloudProposalException.class, () -> agent.propose(AgentRequest.of("hello", user)));
    }
}
