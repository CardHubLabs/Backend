package com.kant.cardhub.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    private UUID id;

    @Column(length = 320)
    private String email;

    @Column(nullable = false, length = 30)
    private String nickname;

    private LocalDate birthDate;

    private Instant termsAgreedAt;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private UserRole role;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private UserStatus status;

    private Instant anonymizedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected User() {
    }

    public User(String email, String nickname) {
        this.id = UUID.randomUUID();
        this.email = email;
        this.nickname = normalizeNickname(nickname);
        this.role = UserRole.USER;
        this.status = UserStatus.ACTIVE;
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

    public void updateProfile(String nickname, LocalDate birthDate, Instant termsAgreedAt) {
        if (nickname != null) {
            this.nickname = normalizeNickname(nickname);
        }
        if (birthDate != null) {
            this.birthDate = birthDate;
        }
        if (termsAgreedAt != null && this.termsAgreedAt == null) {
            this.termsAgreedAt = termsAgreedAt;
        }
    }

    public void suspend() {
        ensureNotWithdrawn();
        this.status = UserStatus.SUSPENDED;
    }

    public void activate() {
        ensureNotWithdrawn();
        this.status = UserStatus.ACTIVE;
    }

    public void withdraw(Instant now) {
        this.email = "withdrawn-" + id + "@invalid.local";
        this.nickname = "탈퇴한 사용자";
        this.birthDate = null;
        this.status = UserStatus.WITHDRAWN;
        this.anonymizedAt = now;
    }

    public boolean onboardingCompleted() {
        return birthDate != null && termsAgreedAt != null;
    }

    private String normalizeNickname(String value) {
        if (value == null || value.isBlank()) {
            return "CardHub 사용자";
        }
        return value.strip();
    }

    private void ensureNotWithdrawn() {
        if (status == UserStatus.WITHDRAWN) {
            throw new IllegalStateException("Withdrawn user cannot change status");
        }
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getNickname() {
        return nickname;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public Instant getTermsAgreedAt() {
        return termsAgreedAt;
    }

    public UserRole getRole() {
        return role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
