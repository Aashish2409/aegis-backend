package com.aegis.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.aegis.common.dto.AuditEvent;
import com.aegis.common.enums.DecisionType;
import com.aegis.common.enums.ExecutionStatus;

class AuditControllerTest {

    private InMemoryAuditService auditService;
    private AuditController controller;

    @BeforeEach
    void setUp() {
        auditService = new InMemoryAuditService();
        controller = new AuditController(auditService);
    }

    @Test
    void logsAndStatsReturnCorrectMetrics() {
        auditService.record(new AuditEvent(UUID.randomUUID().toString(), Instant.now(), "req1", "u1",
                "DB_AGENT", "READ", "employees", DecisionType.ALLOW, "ok", "RULE-1", ExecutionStatus.EXECUTED));
        auditService.record(new AuditEvent(UUID.randomUUID().toString(), Instant.now(), "req2", "u1",
                "DB_AGENT", "DELETE", "employees", DecisionType.BLOCK, "forbidden", "RULE-2", ExecutionStatus.REJECTED));

        assertEquals(2, controller.logs().size());

        AuditController.AuditStats stats = controller.stats();
        assertNotNull(stats);
        assertEquals(2, stats.totalRequests());
        assertEquals(1, stats.executed());
        assertEquals(1, stats.rejected());
        assertEquals(0, stats.pendingReview());
        assertEquals(0, stats.failed());
        assertEquals(1, stats.decisions().get("ALLOW"));
        assertEquals(1, stats.decisions().get("BLOCK"));
    }
}
