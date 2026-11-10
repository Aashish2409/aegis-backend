package com.aegis.cloud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.aegis.common.dto.AgentAction;
import com.aegis.common.dto.ExecutionResult;
import com.aegis.common.enums.AgentType;

class CloudToolExecutorTest {

    private CloudToolExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new CloudToolExecutor();
    }

    private AgentAction action(String operation, String resource, Map<String, Object> params) {
        return new AgentAction(UUID.randomUUID().toString(), AgentType.CLOUD, CloudAgent.AGENT_ID,
                operation, resource, params);
    }

    @Test
    void executeListFilesReturnsFiles() {
        ExecutionResult result = executor.execute(action("LIST_FILES", "files", Map.of()));
        assertTrue(result.success());
        assertTrue(result.data() instanceof List<?> list && !list.isEmpty());
    }

    @Test
    void executeViewVmReturnsVms() {
        ExecutionResult result = executor.execute(action("VIEW_VM", "vms", Map.of()));
        assertTrue(result.success());
        assertTrue(result.data() instanceof List<?> list && !list.isEmpty());
    }

    @Test
    void executeUploadAddsFile() {
        ExecutionResult result = executor.execute(action("UPLOAD", "files", Map.of("path", "s3://test/app.jar")));
        assertTrue(result.success());
        assertTrue(result.data() instanceof Map<?, ?> map && "UPLOADED".equals(map.get("status")));
    }

    @Test
    void executeDownloadFindsExistingFile() {
        ExecutionResult result = executor.execute(action("DOWNLOAD", "files",
                Map.of("path", "s3://aegis-cloud-storage/logs/access-log.txt")));
        assertTrue(result.success());
        assertTrue(result.data() instanceof Map<?, ?> map && "DOWNLOADED".equals(map.get("status")));
    }

    @Test
    void executeTerminateVmRemovesVm() {
        ExecutionResult result = executor.execute(action("TERMINATE_VM", "vms", Map.of("name", "vm-web-prod")));
        assertTrue(result.success());
    }

    @Test
    void executeFailsOnUnknownOperation() {
        ExecutionResult result = executor.execute(action("EXPLODE", "vms", Map.of()));
        assertFalse(result.success());
    }
}
