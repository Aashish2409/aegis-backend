package com.aegis.cloud;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Component;

import com.aegis.common.api.ToolExecutor;
import com.aegis.common.dto.AgentAction;
import com.aegis.common.dto.ExecutionResult;
import com.aegis.common.enums.AgentType;

/**
 * Runs approved cloud actions against in-memory simulated cloud infrastructure.
 * Only SecurityPipeline may call execute().
 */
@Component
public class CloudToolExecutor implements ToolExecutor {

    private final List<VirtualMachine> vms = new CopyOnWriteArrayList<>();
    private final List<String> files = new CopyOnWriteArrayList<>();

    public CloudToolExecutor() {
        vms.add(new VirtualMachine("vm-001", "vm-web-prod", "us-east-1", "RUNNING", "t3.medium"));
        vms.add(new VirtualMachine("vm-002", "vm-api-prod", "us-east-1", "RUNNING", "t3.large"));
        vms.add(new VirtualMachine("vm-003", "vm-db-replica", "us-west-2", "STOPPED", "r5.xlarge"));

        files.add("s3://aegis-cloud-storage/backups/db-2026-11-01.tar.gz");
        files.add("s3://aegis-cloud-storage/configs/prod-cluster.yaml");
        files.add("s3://aegis-cloud-storage/logs/access-log.txt");
    }

    @Override
    public AgentType supports() {
        return AgentType.CLOUD;
    }

    @Override
    public ExecutionResult execute(AgentAction action) {
        if (action == null || action.operation() == null || action.resource() == null) {
            return ExecutionResult.failure("Invalid action");
        }

        String operation = action.operation().trim().toUpperCase(Locale.ROOT);
        Map<String, Object> params = action.parameters() == null ? Map.of() : action.parameters();

        return switch (operation) {
            case "LIST_FILES" -> ExecutionResult.success(List.copyOf(files));
            case "VIEW_VM", "READ" -> {
                String name = (String) params.get("name");
                if (name != null && !name.isBlank()) {
                    List<VirtualMachine> matched = vms.stream()
                            .filter(vm -> vm.name().equalsIgnoreCase(name.trim()) || vm.id().equalsIgnoreCase(name.trim()))
                            .toList();
                    yield ExecutionResult.success(matched);
                }
                yield ExecutionResult.success(List.copyOf(vms));
            }
            case "SEARCH" -> {
                String query = (String) params.getOrDefault("query", "");
                String qLower = query.toLowerCase(Locale.ROOT);
                List<VirtualMachine> matchedVms = vms.stream()
                        .filter(v -> v.name().toLowerCase(Locale.ROOT).contains(qLower)
                                || v.region().toLowerCase(Locale.ROOT).contains(qLower)
                                || v.status().toLowerCase(Locale.ROOT).contains(qLower))
                        .toList();
                yield ExecutionResult.success(matchedVms);
            }
            case "REPORT" -> {
                long running = vms.stream().filter(v -> "RUNNING".equalsIgnoreCase(v.status())).count();
                Map<String, Object> stats = Map.of(
                        "totalVms", vms.size(),
                        "runningVms", running,
                        "totalFiles", files.size()
                );
                yield ExecutionResult.success(stats);
            }
            case "UPLOAD" -> {
                String path = (String) params.get("path");
                if (path == null || path.isBlank()) {
                    yield ExecutionResult.failure("UPLOAD requires 'path' parameter");
                }
                files.add(path);
                yield ExecutionResult.success(Map.of("status", "UPLOADED", "path", path));
            }
            case "DOWNLOAD" -> {
                String path = (String) params.get("path");
                if (path == null || path.isBlank()) {
                    yield ExecutionResult.failure("DOWNLOAD requires 'path' parameter");
                }
                boolean exists = files.stream().anyMatch(f -> f.equalsIgnoreCase(path));
                if (!exists) {
                    yield ExecutionResult.failure("File not found: " + path);
                }
                yield ExecutionResult.success(Map.of("status", "DOWNLOADED", "path", path, "sizeBytes", 1024));
            }
            case "TERMINATE_VM" -> {
                String vmId = (String) params.getOrDefault("id", params.get("name"));
                if (vmId == null || vmId.isBlank()) {
                    yield ExecutionResult.failure("TERMINATE_VM requires 'id' or 'name'");
                }
                boolean removed = vms.removeIf(vm -> vm.id().equalsIgnoreCase(vmId) || vm.name().equalsIgnoreCase(vmId));
                if (!removed) {
                    yield ExecutionResult.failure("VM not found for termination: " + vmId);
                }
                yield ExecutionResult.success(Map.of("status", "TERMINATED", "vmId", vmId));
            }
            default -> ExecutionResult.failure("Unsupported cloud operation: " + operation);
        };
    }
}
