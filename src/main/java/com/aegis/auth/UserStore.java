package com.aegis.auth;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.aegis.common.enums.UserRole;

/** In-memory user store (lost on restart, like audit and review in this phase). */
@Component
public class UserStore {

    private final Map<String, User> byUsername = new HashMap<>();
    private final Map<String, String> usernameByEmail = new HashMap<>();

    public synchronized User add(User user) {
        if (byUsername.containsKey(user.username()) || usernameByEmail.containsKey(user.email())) {
            throw new DuplicateUserException();
        }
        byUsername.put(user.username(), user);
        usernameByEmail.put(user.email(), user.username());
        return user;
    }

    public synchronized Optional<User> findByUsername(String username) {
        return Optional.ofNullable(byUsername.get(username));
    }

    public synchronized Optional<User> updateRole(String username, UserRole role) {
        User old = byUsername.get(username);
        if (old == null) {
            return Optional.empty();
        }
        User updated = new User(old.userId(), old.username(), old.email(), old.passwordHash(), role);
        byUsername.put(username, updated);
        return Optional.of(updated);
    }
}