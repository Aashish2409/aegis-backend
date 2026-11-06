package com.aegis.common.dto;

import com.aegis.common.enums.UserRole;

/**
 * Who is making the request.
 * IMPORTANT: this is built on the server from the verified JWT. It must NEVER
 * be read from the request body, or a user could claim to be an admin.
 */
public record UserContext(String userId, UserRole role) {
}
