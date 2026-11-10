package com.aegis.email;

import java.time.Instant;

/**
 * Record representing a simulated sent email.
 */
public record SentEmail(
        String emailId,
        String to,
        String subject,
        String body,
        Instant sentAt
) {
}
