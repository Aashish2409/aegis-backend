package com.aegis.email;

public record EmailRecord(
        String id,
        String from,
        String to,
        String subject,
        String body,
        String timestamp
) {}
