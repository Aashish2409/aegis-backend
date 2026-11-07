package com.aegis.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.aegis.common.dto.AgentAction;
import com.aegis.common.dto.AgentRequest;
import com.aegis.common.dto.UserContext;
import com.aegis.common.enums.AgentType;
import com.aegis.common.enums.UserRole;
import com.aegis.common.llm.MockLlmClient;

class DatabaseAgentTest {

    private static final UserContext USER = new UserContext("u1", UserRole.USER);

    private AgentAction propose(String llmOutput) {
        return new DatabaseAgent((system, user) -> llmOutput).propose(AgentRequest.of("anything", USER));
    }

    @Test
    void validReadFromMockLlm() {
        AgentAction action = new DatabaseAgent(new MockLlmClient())
                .propose(AgentRequest.of("show IT employees", USER));

        assertEquals("DB_AGENT_01", action.agentId());
        assertEquals(AgentType.DATABASE, action.agentType());
        assertEquals("READ", action.operation());
        assertEquals("employees", action.resource());
        assertEquals("IT", action.parameters().get("department"));
    }

    @Test
    void validSearch() {
        AgentAction action = propose("{\"operation\":\"search\",\"resource\":\"employees\",\"filter\":{\"name\":\"priya\"}}");
        assertEquals("SEARCH", action.operation());
        assertEquals("priya", action.parameters().get("name"));
    }

    @Test
    void validReportWithoutFilter() {
        AgentAction action = propose("{\"operation\":\"REPORT\",\"resource\":\"employees\"}");
        assertEquals("REPORT", action.operation());
        assertTrue(action.parameters().isEmpty());
    }

    @Test
    void deleteIsProposedSoThePolicyEngineCanBlockIt() {
        AgentAction action = new DatabaseAgent(new MockLlmClient())
                .propose(AgentRequest.of("delete all employees", USER));
        assertEquals("DELETE", action.operation());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not json",
            "",
            "null",
            "[]",
            "{\"resource\":\"employees\"}",
            "{\"operation\":\"READ\"}",
            "{\"operation\":\"\",\"resource\":\"employees\"}",
            "{\"operation\":123,\"resource\":\"employees\"}",
            "{\"operation\":\"READ\",\"resource\":[\"employees\"]}",
            "{\"operation\":\"READ\",\"resource\":\"employees\",\"filter\":[\"IT\"]}",
            "{\"operation\":\"READ\",\"resource\":\"employees\",\"filter\":{\"department\":5}}",
            "{\"operation\":\"READ\",\"resource\":\"employees\",\"filter\":{\"department\":{\"a\":\"b\"}}}",
            "{\"operation\":\"READ\",\"resource\":\"employees\",\"extra\":\"x\"}",
            "{\"operation\":\"DROP TABLE employees;\",\"resource\":\"employees\"}",
            "{\"operation\":\"READ\",\"resource\":\"employees\"} trailing"
    })
    void malformedProposalsFailClosed(String llmOutput) {
        assertEquals(DatabaseProposalException.class,
                assertThrows(DatabaseProposalException.class, () -> propose(llmOutput)).getClass());
    }

    @Test
    void nullLlmOutputFailsClosed() {
        assertEquals(DatabaseProposalException.class,
                assertThrows(DatabaseProposalException.class, () -> propose(null)).getClass());
    }
}