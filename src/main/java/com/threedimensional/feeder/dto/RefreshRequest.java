package com.threedimensional.feeder.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /auth/refresh}.
 * The opaque refresh token value issued by this API at sign-in or at the previous refresh.
 */
public record RefreshRequest(@NotBlank String refreshToken) {
}
