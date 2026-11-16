package com.threedimensional.feeder.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /auth/revoke}.
 * The opaque refresh token that should be invalidated (logout from the current session).
 */
public record RevokeRequest(@NotBlank String refreshToken) {
}
