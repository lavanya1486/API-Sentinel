package dev.apisentinel.web;

import dev.apisentinel.dto.AuthDtos;
import dev.apisentinel.security.ApiUserPrincipal;
import dev.apisentinel.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/account")
public class AccountController {
    private final AuthService authService;
    public AccountController(AuthService authService) { this.authService = authService; }

    @GetMapping
    public AuthDtos.AccountResponse account(@AuthenticationPrincipal ApiUserPrincipal principal) {
        return authService.account(principal.id());
    }

    @PostMapping("/password")
    public org.springframework.http.ResponseEntity<Void> changePassword(
        @AuthenticationPrincipal ApiUserPrincipal principal,
        @Valid @RequestBody AuthDtos.ChangePasswordRequest request) {
        authService.changePassword(principal.id(), request);
        return org.springframework.http.ResponseEntity.noContent().build();
    }
}
