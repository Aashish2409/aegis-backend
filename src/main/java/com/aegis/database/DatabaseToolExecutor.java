package com.aegis.database;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.aegis.common.api.ToolExecutor;
import com.aegis.common.dto.AgentAction;
import com.aegis.common.dto.ExecutionResult;
import com.aegis.common.enums.AgentType;

/** Runs approved database actions against a mock in-memory employee list. Only SecurityPipeline calls it. */
@Component
public class DatabaseToolExecutor implements ToolExecutor {

    private static final Set<String> FILTER_KEYS = Set.of("id", "name", "department", "designation");

    private static final List<Employee> EMPLOYEES = List.of(
            new Employee("E001", "Aarav Sharma", "IT", "Software Engineer"),
            new Employee("E002", "Priya Nair", "IT", "DevOps Engineer"),
            new Employee("E003", "Rohan Das", "IT", "Security Analyst"),
            new Employee("E004", "Meera Iyer", "HR", "HR Manager"),
            new Employee("E005", "Sanjay Gupta", "HR", "Recruiter"),
            new Employee("E006", "Neha Verma", "Finance", "Accountant"),
            new Employee("E007", "Vikram Singh", "Finance", "Financial Analyst"),
            new Employee("E008", "Ananya Rao", "Marketing", "Marketing Lead"));

    @Override
    public AgentType supports() {
        return AgentType.DATABASE;
    }

    @Override
    public ExecutionResult execute(AgentAction action) {
        if (action == null || action.operation() == null || action.resource() == null) {
            return ExecutionResult.failure("Invalid action");
        }
        if (!"employees".equalsIgnoreCase(action.resource().trim())) {
            return ExecutionResult.failure("Unsupported resource: " + action.resource());
        }
        for (String key : action.parameters().keySet()) {
            if (!FILTER_KEYS.contains(key)) {
                return ExecutionResult.failure("Unsupported filter: " + key);
            }
        }

        String operation = action.operation().trim().toUpperCase(Locale.ROOT);
        return switch (operation) {
            case "READ" -> ExecutionResult.success(filter(action.parameters()));
            case "SEARCH" -> {
                if (action.parameters().isEmpty()) {
                    yield ExecutionResult.failure("SEARCH requires at least one filter");
                }
                yield ExecutionResult.success(filter(action.parameters()));
            }
            case "REPORT" -> ExecutionResult.success(report(filter(action.parameters())));
            default -> ExecutionResult.failure("Unsupported operation: " + action.operation());
        };
    }

    private List<Employee> filter(Map<String, Object> filters) {
        return EMPLOYEES.stream().filter(e -> matches(e, filters)).toList();
    }

    private boolean matches(Employee e, Map<String, Object> filters) {
        for (Map.Entry<String, Object> f : filters.entrySet()) {
            String wanted = String.valueOf(f.getValue()).trim();
            boolean ok = switch (f.getKey()) {
                case "id" -> e.id().equalsIgnoreCase(wanted);
                case "department" -> e.department().equalsIgnoreCase(wanted);
                case "designation" -> e.designation().equalsIgnoreCase(wanted);
                case "name" -> e.name().toLowerCase(Locale.ROOT).contains(wanted.toLowerCase(Locale.ROOT));
                default -> false;
            };
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    private Map<String, Object> report(List<Employee> employees) {
        Map<String, Long> byDepartment = employees.stream()
                .collect(Collectors.groupingBy(Employee::department, TreeMap::new, Collectors.counting()));
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("totalEmployees", employees.size());
        report.put("byDepartment", byDepartment);
        return report;
    }
}