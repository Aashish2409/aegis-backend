package com.aegis.common.enums;

/** Roles used for role-based access control. */
public enum UserRole {
    USER,      // normal user
    REVIEWER,  // may approve or reject REVIEW items
    ADMIN      // may manage policies and agents
}
