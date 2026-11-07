package com.aegis.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.aegis.common.enums.UserRole;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api")
public class AuthController {

    public record RegisterRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9_.-]{3,50}$") String username,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 8, max = 72) String password) {
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record RoleRequest(@NotNull UserRole role) {
    }

    public record UserResponse(String userId, String username, UserRole role) {
    }

    public record TokenResponse(String token, String tokenType, long expiresInSeconds) {
    }

    private final AuthService authService;
    private final UserStore store;
    private final JwtService jwtService;

    public AuthController(AuthService authService, UserStore store, JwtService jwtService) {
        this.authService = authService;
        this.store = store;
        this.jwtService = jwtService;
    }

    @PostMapping("/auth/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest body) {
        try {
            User user = authService.register(body.username(), body.email(), body.password());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new UserResponse(user.userId(), user.username(), user.role()));
        } catch (DuplicateUserException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @PostMapping("/auth/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest body) {
        try {
            String token = authService.login(body.username(), body.password());
            return new TokenResponse(token, "Bearer", jwtService.expiresInSeconds());
        } catch (BadCredentialsException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
    }

    /** ADMIN only (enforced in SecurityConfig): how REVIEWER/ADMIN roles get assigned. */
    @PutMapping("/admin/users/{username}/role")
    public UserResponse changeRole(@PathVariable String username, @Valid @RequestBody RoleRequest body) {
        User user = store.updateRole(AuthService.normalize(username), body.role())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return new UserResponse(user.userId(), user.username(), user.role());
    }
}