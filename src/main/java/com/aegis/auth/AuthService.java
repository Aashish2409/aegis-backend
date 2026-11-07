package com.aegis.auth;

import java.util.Locale;
import java.util.UUID;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.aegis.common.enums.UserRole;

@Service
public class AuthService {

    private final UserStore store;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;
    private final String dummyHash; // used so unknown users cost the same time as wrong passwords

    public AuthService(UserStore store, PasswordEncoder encoder, JwtService jwtService) {
        this.store = store;
        this.encoder = encoder;
        this.jwtService = jwtService;
        this.dummyHash = encoder.encode("aegis-dummy-password");
    }

    /** Self-registration ALWAYS creates a USER. Roles are never taken from the request. */
    public User register(String username, String email, String rawPassword) {
        return store.add(new User(UUID.randomUUID().toString(), normalize(username),
                normalize(email), encoder.encode(rawPassword), UserRole.USER));
    }

    public String login(String username, String rawPassword) {
        User user = store.findByUsername(normalize(username)).orElse(null);
        boolean passwordOk = encoder.matches(rawPassword, user == null ? dummyHash : user.passwordHash());
        if (user == null || !passwordOk) {
            throw new BadCredentialsException("Invalid credentials");
        }
        return jwtService.generate(user);
    }

    static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}