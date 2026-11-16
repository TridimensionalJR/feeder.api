package com.threedimensional.feeder.controller;

import com.threedimensional.feeder.dto.AuthResponse;
import com.threedimensional.feeder.dto.GoogleLoginRequest;
import com.threedimensional.feeder.dto.RefreshRequest;
import com.threedimensional.feeder.dto.RevokeRequest;
import com.threedimensional.feeder.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoints. All three are public by design: the caller has no application token
 * at the time they call any of them.
 * <p>
 * <ul>
 *   <li>{@code POST /auth/google} — exchanges a Google ID token for an access + refresh token pair.
 *   <li>{@code POST /auth/refresh} — rotates a refresh token and returns a new pair.
 *   <li>{@code POST /auth/revoke} — revokes a refresh token (single-session logout).
 * </ul>
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/google")
    public AuthResponse loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        return AuthResponse.from(authService.loginWithGoogle(request.idToken()));
    }

    /**
     * Rotates the given refresh token. The old token is immediately revoked; a new access
     * token and a new refresh token are returned. The client must store the new refresh
     * token and discard the old one.
     */
    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return AuthResponse.from(authService.refresh(request.refreshToken()));
    }

    /**
     * Revokes the given refresh token (logout from the current session). Unknown or
     * already-revoked tokens are silently ignored so the response is always 204.
     */
    @PostMapping("/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@Valid @RequestBody RevokeRequest request) {
        authService.revoke(request.refreshToken());
    }
}
