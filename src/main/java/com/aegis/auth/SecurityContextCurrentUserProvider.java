package com.aegis.auth;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import com.aegis.common.api.CurrentUserProvider;
import com.aegis.common.dto.UserContext;
import com.aegis.common.enums.UserRole;

/** Builds the UserContext from the already-validated JWT in the SecurityContext. */
@Component
public class SecurityContextCurrentUserProvider implements CurrentUserProvider {

    @Override
    public UserContext getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw new AuthenticationCredentialsNotFoundException("No authenticated user");
        }
        String userId = jwt.getSubject();
        String role = jwt.getClaimAsString(JwtService.ROLE_CLAIM);
        if (userId == null || userId.isBlank() || role == null) {
            throw new BadCredentialsException("Token has no user identity");
        }
        try {
            return new UserContext(userId, UserRole.valueOf(role));
        } catch (IllegalArgumentException e) {
            throw new BadCredentialsException("Token has an unknown role");
        }
    }
}