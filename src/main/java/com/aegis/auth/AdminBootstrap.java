package com.aegis.auth;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.aegis.common.enums.UserRole;

@Component
public class AdminBootstrap implements ApplicationRunner {

    private final UserStore store;
    private final PasswordEncoder encoder;
    private final String username;
    private final String password;

    public AdminBootstrap(UserStore store, PasswordEncoder encoder,
                          @Value("${aegis.admin.username:}") String username,
                          @Value("${aegis.admin.password:}") String password) {
        this.store = store;
        this.encoder = encoder;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (username.isBlank() || password.length() < 8) {
            return;
        }
        try {
            String name = AuthService.normalize(username);
            store.add(new User(UUID.randomUUID().toString(), name, name + "@aegis.local",
                    encoder.encode(password), UserRole.ADMIN));
        } catch (DuplicateUserException ignored) {
            // already seeded
        }
    }
}