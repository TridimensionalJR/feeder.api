package com.threedimensional.feeder.dto;

import com.threedimensional.feeder.service.AuthService;

/**
 * The answer to a successful sign-in or token refresh. {@code expiresIn} and
 * {@code refreshExpiresIn} are in seconds so the front can schedule silent refreshes
 * without decoding either token. The profile comes along so the front can show who is
 * signed in without a second round trip.
 */
public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        String refreshToken,
        long refreshExpiresIn,
        UserResponse user) {

    private static final String BEARER = "Bearer";

    public static AuthResponse from(AuthService.LoginResult result) {
        long refreshExpiresIn = java.time.Duration.between(
                java.time.Instant.now(),
                result.refreshToken().getExpiresAt()).getSeconds();

        return new AuthResponse(
                result.accessToken().value(),
                BEARER,
                result.accessToken().expiresIn().toSeconds(),
                result.refreshToken().getToken(),
                refreshExpiresIn,
                UserResponse.from(result.user()));
    }
}
