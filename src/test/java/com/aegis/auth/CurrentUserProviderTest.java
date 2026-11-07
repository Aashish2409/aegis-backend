package com.aegis.auth;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.aegis.common.dto.UserContext;
import com.aegis.common.enums.UserRole;

class CurrentUserProviderTest {

    private final SecurityContextCurrentUserProvider provider = new SecurityContextCurrentUserProvider();

    @AfterEach
    @SuppressWarnings("unused")
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateWith(String subject, String role) {
        Jwt.Builder builder = Jwt.withTokenValue("t").header("alg", "HS256");
        if (subject != null) builder.subject(subject);
        if (role != null) builder.claim("role", role);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(builder.build()));
    }

    @Test
    void readsIdAndRoleFromSecurityContext() {
        for (UserRole role : UserRole.values()) {
            authenticateWith("user-42", role.name());
            assertEquals(new UserContext("user-42", role), provider.getCurrentUser());
        }
    }

    @Test
    void noAuthenticationFailsClosed() {
        AuthenticationCredentialsNotFoundException exception = assertThrows(
            AuthenticationCredentialsNotFoundException.class, provider::getCurrentUser);
        assertEquals(AuthenticationCredentialsNotFoundException.class, exception.getClass());
    }

    @Test
    void unknownOrMissingRoleFailsClosed() {
        authenticateWith("user-42", "SUPERUSER");
        BadCredentialsException unknownRoleException = assertThrows(
            BadCredentialsException.class, provider::getCurrentUser);
        assertEquals(BadCredentialsException.class, unknownRoleException.getClass());
        authenticateWith("user-42", null);
        BadCredentialsException exception = assertThrows(BadCredentialsException.class, provider::getCurrentUser);
        assertEquals(BadCredentialsException.class, exception.getClass());
    }
}