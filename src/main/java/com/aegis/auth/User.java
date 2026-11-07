package com.aegis.auth;

import com.aegis.common.enums.UserRole;

public record User(String userId, String username, String email, String passwordHash, UserRole role) {
}