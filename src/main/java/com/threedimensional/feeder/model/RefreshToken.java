package com.threedimensional.feeder.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import jakarta.persistence.EntityListeners;

import java.time.Instant;
import java.util.UUID;

/**
 * A one-time refresh credential stored server-side.
 * <p>
 * The token value is an opaque 64-character hex string (32 random bytes) generated
 * by {@code RefreshTokenService}. When a client presents it at {@code /auth/refresh}
 * the row is immediately revoked and a fresh token is issued in its place (rotation).
 * <p>
 * Rows are never physically deleted on revocation so that a stolen token that arrives
 * after the legitimate holder has already rotated it can be detected: the row exists
 * but {@code revoked} is {@code true}. At that point the entire family of sessions for
 * that user can be invalidated.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** The opaque value sent to the client. Never the user's internal id. */
    @Column(name = "token", nullable = false, length = 64, unique = true)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /**
     * Whether this token has been consumed or explicitly revoked. A {@code true} value
     * arriving at the refresh endpoint signals a potential replay attack.
     */
    @Column(name = "revoked", nullable = false)
    private boolean revoked = false;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Required by JPA. */
    protected RefreshToken() {
    }

    private RefreshToken(String token, User user, Instant expiresAt) {
        this.token = token;
        this.user = user;
        this.expiresAt = expiresAt;
    }

    /** Constructs a new, non-revoked refresh token for the given user. */
    public static RefreshToken create(String token, User user, Instant expiresAt) {
        return new RefreshToken(token, user, expiresAt);
    }

    /** Marks this token as consumed or invalidated. Idempotent. */
    public void revoke() {
        this.revoked = true;
    }

    public UUID getId() {
        return id;
    }

    public String getToken() {
        return token;
    }

    public User getUser() {
        return user;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
