package com.aegis.audit;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aegis.common.api.AuditService;
import com.aegis.common.dto.AuditEvent;
import com.aegis.common.enums.DecisionType;
import com.aegis.common.enums.ExecutionStatus;

/**
 * Controller for viewing audit logs and security dashboard statistics (owner: Aashish).
 */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    public record AuditStats(
            long totalRequests,
            long executed,
            long rejected,
            long pendingReview,
            long failed,
            Map<String, Long> decisions
    ) {}

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/logs")
    public List<AuditEvent> logs() {
        return auditService.findAll();
    }

    @GetMapping("/stats")
    public AuditStats stats() {
        List<AuditEvent> all = auditService.findAll();
        long executed = all.stream().filter(e -> e.status() == ExecutionStatus.EXECUTED).count();
        long rejected = all.stream().filter(e -> e.status() == ExecutionStatus.REJECTED).count();
        long pending = all.stream().filter(e -> e.status() == ExecutionStatus.PENDING_REVIEW).count();
        long failed = all.stream().filter(e -> e.status() == ExecutionStatus.FAILED).count();

        Map<String, Long> decisions = all.stream()
                .filter(e -> e.decision() != null)
                .collect(Collectors.groupingBy(e -> e.decision().name(), Collectors.counting()));

        return new AuditStats(all.size(), executed, rejected, pending, failed, decisions);
    }
}
