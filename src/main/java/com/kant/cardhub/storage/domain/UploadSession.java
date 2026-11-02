package com.kant.cardhub.storage.domain;

import com.kant.cardhub.common.error.BusinessException;
import com.kant.cardhub.common.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "upload_sessions")
public class UploadSession {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "storage_key", nullable = false, unique = true, length = 500)
    private String storageKey;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UploadStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected UploadSession() {
    }

    private UploadSession(
            UUID id,
            UUID ownerId,
            String storageKey,
            String originalFilename,
            String contentType,
            long fileSize,
            Instant expiresAt,
            Instant now
    ) {
        this.id = Objects.requireNonNull(id);
        this.ownerId = Objects.requireNonNull(ownerId);
        this.storageKey = Objects.requireNonNull(storageKey);
        this.originalFilename = Objects.requireNonNull(originalFilename);
        this.contentType = Objects.requireNonNull(contentType);
        this.fileSize = fileSize;
        this.status = UploadStatus.PENDING;
        this.expiresAt = Objects.requireNonNull(expiresAt);
        this.createdAt = Objects.requireNonNull(now);
        this.updatedAt = now;
    }

    public static UploadSession create(
            UUID ownerId,
            String storageKey,
            String originalFilename,
            String contentType,
            long fileSize,
            Instant expiresAt,
            Instant now
    ) {
        return new UploadSession(
                UUID.randomUUID(), ownerId, storageKey, originalFilename, contentType, fileSize, expiresAt, now
        );
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public String getStorageKey() { return storageKey; }
    public String getOriginalFilename() { return originalFilename; }
    public String getContentType() { return contentType; }
    public long getFileSize() { return fileSize; }
    public UploadStatus getStatus() { return status; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public Instant getCreatedAt() { return createdAt; }

    public void verify(String actualContentType, long actualSize, Instant now) {
        requireOwnerState(UploadStatus.PENDING);
        if (!now.isBefore(expiresAt)) {
            expire(now);
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "업로드 세션이 만료되었습니다.");
        }
        if (!contentType.equalsIgnoreCase(actualContentType) || fileSize != actualSize) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "업로드된 파일의 형식 또는 크기가 요청과 다릅니다.");
        }
        this.status = UploadStatus.VERIFIED;
        this.verifiedAt = now;
        this.updatedAt = now;
    }

    public void attach(UUID requesterId, Instant now) {
        if (!ownerId.equals(requesterId)) throw new BusinessException(ErrorCode.FORBIDDEN);
        requireOwnerState(UploadStatus.VERIFIED);
        this.status = UploadStatus.ATTACHED;
        this.updatedAt = now;
    }

    public void expire(Instant now) {
        if (status == UploadStatus.ATTACHED) return;
        this.status = UploadStatus.EXPIRED;
        this.updatedAt = now;
    }

    private void requireOwnerState(UploadStatus expected) {
        if (status != expected) {
            throw new BusinessException(ErrorCode.CONFLICT, "현재 업로드 상태에서는 처리할 수 없습니다.");
        }
    }
}
