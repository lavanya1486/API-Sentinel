package dev.apisentinel.service;

import dev.apisentinel.domain.AppUser;
import dev.apisentinel.dto.AuthDtos;
import dev.apisentinel.repository.AppUserRepository;
import dev.apisentinel.security.JwtService;
import dev.apisentinel.web.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private static final String DUMMY_HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(AppUserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request) {
        String email = normalizeEmail(request.email());
        validatePasswordBytes(request.password());
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("EMAIL_IN_USE", "An account with this email already exists");
        }
        AppUser user = users.save(new AppUser(email, passwordEncoder.encode(request.password())));
        return authResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
        String email = normalizeEmail(request.email());
        String password = request.password();
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            passwordEncoder.matches("invalid", DUMMY_HASH);
            throw invalidCredentials();
        }
        AppUser user = users.findByEmailIgnoreCase(email).orElse(null);
        String hash = user == null ? DUMMY_HASH : user.getPasswordHash();
        if (!passwordEncoder.matches(password, hash) || user == null) throw invalidCredentials();
        return authResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthDtos.AccountResponse account(UUID userId) {
        AppUser user = users.findById(userId).orElseThrow(() -> ApiException.notFound("Account"));
        return new AuthDtos.AccountResponse(user.getId(), user.getEmail(), user.getCreatedAt());
    }

    @Transactional
    public void changePassword(UUID userId, AuthDtos.ChangePasswordRequest request) {
        validatePasswordBytes(request.newPassword());
        AppUser user = users.findById(userId).orElseThrow(() -> ApiException.notFound("Account"));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("INVALID_CURRENT_PASSWORD", "Current password is incorrect");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("PASSWORD_UNCHANGED", "Choose a password different from the current one");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.incrementTokenVersion(); // Revoke every previously issued access token.
    }

    private AuthDtos.AuthResponse authResponse(AppUser user) {
        return new AuthDtos.AuthResponse(jwtService.issue(user.getId(), user.getTokenVersion()), "Bearer",
            jwtService.ttlSeconds(), new AuthDtos.UserSummary(user.getId(), user.getEmail(), user.getCreatedAt()));
    }

    private static ApiException invalidCredentials() {
        return new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
            "Email or password is incorrect");
    }

    private static String normalizeEmail(String email) { return email.trim().toLowerCase(java.util.Locale.ROOT); }

    private static void validatePasswordBytes(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw ApiException.badRequest("PASSWORD_TOO_LONG", "Password must be at most 72 UTF-8 bytes");
        }
    }
}
