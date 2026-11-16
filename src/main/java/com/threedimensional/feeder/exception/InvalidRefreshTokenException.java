package com.threedimensional.feeder.exception;

/**
 * Thrown when a refresh token is missing, expired, or revoked.
 * <p>
 * The message is internal and carries enough detail to understand what
 * went wrong in the logs. The HTTP response exposes only a fixed phrase,
 * never the token value or the specific reason.
 */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}
