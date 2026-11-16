package com.threedimensional.feeder.service;

import com.threedimensional.feeder.config.AuthProperties;
import com.threedimensional.feeder.config.JwtConfig;
import com.threedimensional.feeder.exception.InvalidRefreshTokenException;
import com.threedimensional.feeder.model.RefreshToken;
import com.threedimensional.feeder.model.User;
import com.threedimensional.feeder.repository.RefreshTokenRepository;
import com.threedimensional.feeder.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the lifecycle of refresh tokens against a real repository and an in-memory database.
 * {@link JwtConfig} supplies the shared {@link Clock} bean. All time-sensitive assertions use a
 * fixed clock injected via {@link ReflectionTestUtils} so the tests are deterministic.
 */
@DataJpaTest
@Import({RefreshTokenService.class, JwtConfig.class})
class RefreshTokenServiceTests {

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
    private static final Clock FIXED = Clock.fixed(NOW, ZoneOffset.UTC);

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthProperties properties;

    @Autowired
    private RefreshTokenService refreshTokenService;

    // ---- issue -------------------------------------------------------------------------------

    @Test
    void issue_shouldPersistTokenWithCorrectExpiry() {
        injectClock(FIXED);
        User user = savedUser();

        RefreshToken token = refreshTokenService.issue(user);

        assertThat(token.getId()).isNotNull();
        assertThat(token.getToken()).hasSize(64).matches("[0-9a-f]+");
        assertThat(token.isRevoked()).isFalse();
        assertThat(token.getExpiresAt())
                .isEqualTo(NOW.plus(properties.jwt().refreshTokenExpiration()));
        assertThat(refreshTokenRepository.count()).isEqualTo(1);
    }

    @Test
    void issue_shouldGenerateUniqueTokenValues() {
        injectClock(FIXED);
        User user = savedUser();

        RefreshToken first = refreshTokenService.issue(user);
        RefreshToken second = refreshTokenService.issue(user);

        assertThat(first.getToken()).isNotEqualTo(second.getToken());
    }

    // ---- rotate ------------------------------------------------------------------------------

    @Test
    void rotate_shouldRevokeOldTokenAndReturnNewOne() {
        injectClock(FIXED);
        User user = savedUser();
        RefreshToken issued = refreshTokenService.issue(user);

        RefreshToken rotated = refreshTokenService.rotate(issued.getToken());

        // The original token is revoked in the database.
        RefreshToken reloaded = refreshTokenRepository.findByToken(issued.getToken()).orElseThrow();
        assertThat(reloaded.isRevoked()).isTrue();

        // The new token is different and not revoked.
        assertThat(rotated.getToken()).isNotEqualTo(issued.getToken());
        assertThat(rotated.isRevoked()).isFalse();
        assertThat(rotated.getUser().getId()).isEqualTo(user.getId());
    }

    @Test
    void rotate_shouldThrow_whenTokenDoesNotExist() {
        assertThatThrownBy(() -> refreshTokenService.rotate("0".repeat(64)))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void rotate_shouldThrow_whenTokenIsAlreadyRevoked() {
        injectClock(FIXED);
        User user = savedUser();
        RefreshToken issued = refreshTokenService.issue(user);
        // Simulate the token already having been used.
        issued.revoke();
        refreshTokenRepository.saveAndFlush(issued);

        assertThatThrownBy(() -> refreshTokenService.rotate(issued.getToken()))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessageContaining("already revoked");
    }

    @Test
    void rotate_shouldRevokeAllSessionsWhenRevokedTokenIsReused() {
        injectClock(FIXED);
        User user = savedUser();

        // Two active sessions for the same user.
        RefreshToken sessionA = refreshTokenService.issue(user);
        RefreshToken sessionB = refreshTokenService.issue(user);

        // Session A is used once and rotated (legitimate use).
        refreshTokenService.rotate(sessionA.getToken());

        // The original token of session A is now revoked. Re-presenting it signals a replay attack.
        assertThatThrownBy(() -> refreshTokenService.rotate(sessionA.getToken()))
                .isInstanceOf(InvalidRefreshTokenException.class);

        // All sessions for this user should now be revoked.
        RefreshToken reloadedB = refreshTokenRepository.findByToken(sessionB.getToken()).orElseThrow();
        assertThat(reloadedB.isRevoked()).isTrue();
    }

    @Test
    void rotate_shouldThrow_whenTokenIsExpired() {
        injectClock(FIXED);
        User user = savedUser();
        RefreshToken issued = refreshTokenService.issue(user);

        // Wind the clock past the expiry.
        Duration expiration = properties.jwt().refreshTokenExpiration();
        injectClock(Clock.fixed(NOW.plus(expiration).plusSeconds(1), ZoneOffset.UTC));

        assertThatThrownBy(() -> refreshTokenService.rotate(issued.getToken()))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessageContaining("expired");
    }

    // ---- revoke ------------------------------------------------------------------------------

    @Test
    void revoke_shouldMarkTokenAsRevoked() {
        injectClock(FIXED);
        User user = savedUser();
        RefreshToken issued = refreshTokenService.issue(user);

        refreshTokenService.revoke(issued.getToken());

        RefreshToken reloaded = refreshTokenRepository.findByToken(issued.getToken()).orElseThrow();
        assertThat(reloaded.isRevoked()).isTrue();
    }

    @Test
    void revoke_shouldBeANoOpForUnknownToken() {
        // Should not throw.
        refreshTokenService.revoke("0".repeat(64));
        assertThat(refreshTokenRepository.count()).isZero();
    }

    @Test
    void revoke_shouldBeIdempotent() {
        injectClock(FIXED);
        User user = savedUser();
        RefreshToken issued = refreshTokenService.issue(user);

        refreshTokenService.revoke(issued.getToken());
        refreshTokenService.revoke(issued.getToken()); // second call must not throw
    }

    // ---- Helpers -----------------------------------------------------------------------------

    private User savedUser() {
        return userRepository.saveAndFlush(User.create("google-abc", "tiago@example.com", "Tiago"));
    }

    private void injectClock(Clock clock) {
        ReflectionTestUtils.setField(refreshTokenService, "clock", clock);
    }
}
