package com.aegis.audit;

import com.aegis.common.api.AuditService;
import com.aegis.common.dto.AuditEvent;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Keeps audit events in memory (lost on restart, by design for this phase).
 * Thread-safe because many requests can arrive at once.
 * Aashish extends this with filtering, statistics and the dashboard.
 */
@Service
public class InMemoryAuditService implements AuditService {

    private final List<AuditEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public void record(AuditEvent event) {
        events.add(event);
    }

    @Override
    public List<AuditEvent> findAll() {
        return List.copyOf(events);
    }
}
