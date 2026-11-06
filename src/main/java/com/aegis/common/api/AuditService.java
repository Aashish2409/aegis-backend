package com.aegis.common.api;

import com.aegis.common.dto.AuditEvent;

import java.util.List;

/** Records and returns audit events. */
public interface AuditService {

    void record(AuditEvent event);

    List<AuditEvent> findAll();
}
