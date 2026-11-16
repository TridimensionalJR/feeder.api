package com.threedimensional.feeder.repository;

import com.threedimensional.feeder.model.RefreshToken;
import com.threedimensional.feeder.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /** Finds a token by its opaque value regardless of revocation status. */
    Optional<RefreshToken> findByToken(String token);

    /**
     * Revokes all active refresh tokens for a user in a single statement.
     * Used when a replay attack is detected or the user signs out from all sessions.
     * <p>
     * {@code flushAutomatically} ensures pending dirty entities are flushed before the JPQL
     * UPDATE runs, so an in-progress revocation from the same transaction is not overwritten.
     * {@code clearAutomatically} clears the persistence context afterwards so subsequent
     * lookups in the same transaction see the updated state.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.user = :user AND rt.revoked = false")
    void revokeAllByUser(User user);

    /**
     * Deletes tokens that have already expired, regardless of revocation status.
     * Intended for a periodic cleanup job, not for the hot path.
     */
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < CURRENT_TIMESTAMP")
    void deleteAllExpired();
}
