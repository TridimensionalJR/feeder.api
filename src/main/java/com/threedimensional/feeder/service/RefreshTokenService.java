package com.threedimensional.feeder.service;

import com.threedimensional.feeder.config.AuthProperties;
import com.threedimensional.feeder.exception.InvalidRefreshTokenException;
import com.threedimensional.feeder.model.RefreshToken;
import com.threedimensional.feeder.model.User;
import com.threedimensional.feeder.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;

/**
 * Lifecycle of refresh tokens: creation, rotation, and revocation.
 * <p>
 * <b>Rotation</b>: every use of a refresh token invalidates it and returns a new one.
 * If a revoked token arrives at {@link #rotate} this service immediately revokes all
 * remaining tokens for that user, treating the event as a potential replay attack
 * (RFC 6819 §5.2.1).
 * <p>
 * Tokens are 32 random bytes encoded as a 64-character lowercase hex string, generated
 * by {@link SecureRandom}. This gives 256 bits of entropy, which is unguessable in any
 * realistic threat model.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    /** 32 bytes → 64 hex characters. */
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                               AuthProperties properties,
                               Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.properties = properties;
        this.clock = clock;
        this.secureRandom = new SecureRandom();
    }

    /**
     * Issues a new refresh token for the given user and persists it.
     *
     * @return the saved {@link RefreshToken}
     */
    @Transactional
    public RefreshToken issue(User user) {
        Instant expiresAt = clock.instant().plus(properties.jwt().refreshTokenExpiration());
        RefreshToken token = RefreshToken.create(generateTokenValue(), user, expiresAt);
        return refreshTokenRepository.save(token);
    }

    /**
     * Validates the given token value, revokes it, and returns a fresh one.
     * <p>
     * If the token is not found, expired, or revoked, {@link InvalidRefreshTokenException}
     * is thrown. A revoked token also triggers revocation of all sessions for that user
     * as a replay-attack countermeasure.
     *
     * @param tokenValue the raw token string from the client
     * @return the newly issued {@link RefreshToken}
     * @throws InvalidRefreshTokenException when the token cannot be trusted
     */
    @Transactional
    public RefreshToken rotate(String tokenValue) {
        RefreshToken existing = refreshTokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new InvalidRefreshTokenException("refresh token not found"));

        if (existing.isRevoked()) {
            // A revoked token being re-presented is a signal that it may have been stolen.
            // Revoke all sessions for this user immediately as a precaution.
            log.warn("Revoked refresh token re-used for user {}; revoking all sessions",
                    existing.getUser().getId());
            refreshTokenRepository.revokeAllByUser(existing.getUser());
            throw new InvalidRefreshTokenException(
                    "refresh token already revoked for user " + existing.getUser().getId());
        }

        if (existing.getExpiresAt().isBefore(clock.instant())) {
            existing.revoke();
            throw new InvalidRefreshTokenException(
                    "refresh token expired for user " + existing.getUser().getId());
        }

        existing.revoke();
        return issue(existing.getUser());
    }

    /**
     * Revokes a single refresh token by its value. No-op when the value is unknown or
     * already revoked. Used for explicit logout from the current session.
     *
     * @param tokenValue the raw token string from the client
     */
    @Transactional
    public void revoke(String tokenValue) {
        refreshTokenRepository.findByToken(tokenValue).ifPresent(RefreshToken::revoke);
    }

    // ---- Private helpers -------------------------------------------------------------------

    private String generateTokenValue() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return bytesToHex(bytes);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
