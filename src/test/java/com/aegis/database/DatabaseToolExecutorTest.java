package com.aegis.database;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.aegis.common.dto.AgentAction;
import com.aegis.common.dto.ExecutionResult;
import com.aegis.common.enums.AgentType;

class DatabaseToolExecutorTest {

    private final DatabaseToolExecutor executor = new DatabaseToolExecutor();

    private ExecutionResult run(String operation, String resource, Map<String, Object> params) {
        return executor.execute(new AgentAction(UUID.randomUUID().toString(), AgentType.DATABASE,
                "DB_AGENT_01", operation, resource, params));
    }

    @Test
    void supportsDatabase() {
        assertEquals(AgentType.DATABASE, executor.supports());
    }

    @Test
    @SuppressWarnings("unchecked")
    void readWithoutFilterReturnsEveryone() {
        ExecutionResult r = run("READ", "employees", Map.of());
        assertTrue(r.success());
        assertEquals(8, ((List<Employee>) r.data()).size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void departmentFilterReturnsOnlyIt() {
        ExecutionResult r = run("READ", "employees", Map.of("department", "IT"));
        List<Employee> result = (List<Employee>) r.data();
        assertEquals(3, result.size());
        assertTrue(result.stream().allMatch(e -> e.department().equals("IT")));
        assertTrue(result.stream().anyMatch(e -> e.name().equals("Rohan Das")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchByNameIsCaseInsensitive() {
        ExecutionResult r = run("SEARCH", "employees", Map.of("name", "PRIYA"));
        List<Employee> result = (List<Employee>) r.data();
        assertEquals(1, result.size());
        assertEquals("E002", result.get(0).id());
    }

    @Test
    void searchWithoutFilterIsRejected() {
        assertFalse(run("SEARCH", "employees", Map.of()).success());
    }

    @Test
    @SuppressWarnings("unchecked")
    void reportSummarisesByDepartment() {
        ExecutionResult r = run("REPORT", "employees", Map.of());
        Map<String, Object> report = (Map<String, Object>) r.data();
        assertEquals(8, report.get("totalEmployees"));
        assertEquals(3L, ((Map<String, Long>) report.get("byDepartment")).get("IT"));
    }

    @Test
    void unsupportedOperationsAreRejected() {
        assertFalse(run("DELETE", "employees", Map.of()).success());
        assertFalse(run("DROP", "employees", Map.of()).success());
        assertFalse(run("", "employees", Map.of()).success());
    }

    @Test
    void unknownResourceAndFilterAreRejected() {
        assertFalse(run("READ", "salaries", Map.of()).success());
        assertFalse(run("READ", "employees", Map.of("password", "x")).success());
    }
}