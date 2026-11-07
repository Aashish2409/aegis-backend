package com.aegis.auth;

import javax.crypto.SecretKey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import com.aegis.auth.config.SecurityConfig;
import com.aegis.common.enums.UserRole;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-0123456789-abcdefghijklmnop";

    private final SecurityConfig config = new SecurityConfig();
    private final SecretKey key = config.jwtSecretKey(SECRET);
    private final JwtService service = new JwtService(config.jwtEncoder(key), 60);
    private final JwtDecoder decoder = config.jwtDecoder(key);

    @Test
    void tokenCarriesUserIdAndRole() {
        for (UserRole role : UserRole.values()) {
            String token = service.generate(new User("id-" + role, "bob", "b@x.com", "hash", role));
            Jwt jwt = decoder.decode(token);
            assertEquals("id-" + role, jwt.getSubject());
            assertEquals(role.name(), jwt.getClaimAsString("role"));
        }
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = service.generate(new User("id-1", "bob", "b@x.com", "hash", UserRole.USER));
        String tampered = token.substring(0, token.length() - 3) + (token.endsWith("AAA") ? "BBB" : "AAA");
        BadJwtException exception = assertThrows(BadJwtException.class, () -> decoder.decode(tampered));
        assertNotNull(exception.getMessage());
    }

    @Test
    void shortSecretIsRefused() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> config.jwtSecretKey("too-short"));
        assertNotNull(exception.getMessage());
    }
}