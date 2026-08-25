package com.kant.cardhub.auth.domain;

import com.kant.cardhub.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_sessions")
public class AuthSession {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "refresh_token_hash", nullable = false, unique = true, length = 64)
    private String refreshTokenHash;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant revokedAt;

    private UUID replacedBySessionId;

    @Column(length = 512)
    private String userAgent;

    @Column(length = 64)
    private String ipHash;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected AuthSession() {
    }

    public AuthSession(
            User user,
            String refreshTokenHash,
            Instant expiresAt,
            String userAgent,
            String ipHash
    ) {
        this.id = UUID.randomUUID();
        this.user = user;
        this.refreshTokenHash = refreshTokenHash;
        this.expiresAt = expiresAt;
        this.userAgent = truncate(userAgent, 512);
        this.ipHash = ipHash;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public boolean expiredAt(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public boolean revoked() {
        return revokedAt != null;
    }

    public void rotate(UUID replacementId, Instant now) {
        revoke(now);
        replacedBySessionId = replacementId;
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }
}
