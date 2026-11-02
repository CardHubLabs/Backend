package com.kant.cardhub.storage.application;

import com.kant.cardhub.common.error.BusinessException;
import com.kant.cardhub.common.error.ErrorCode;
import com.kant.cardhub.storage.domain.UploadSession;
import com.kant.cardhub.storage.infrastructure.UploadSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class UploadService {

    public static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final Duration UPLOAD_TTL = Duration.ofMinutes(15);
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final UploadSessionRepository repository;
    private final ObjectStoragePort objectStorage;
    private final Clock clock;

    public UploadService(UploadSessionRepository repository, ObjectStoragePort objectStorage, Clock clock) {
        this.repository = repository;
        this.objectStorage = objectStorage;
        this.clock = clock;
    }

    @Transactional
    public UploadView.Presigned presign(UUID ownerId, String filename, String contentType, long fileSize) {
        validate(filename, contentType, fileSize);
        Instant now = clock.instant();
        Instant expiresAt = now.plus(UPLOAD_TTL);
        UUID token = UUID.randomUUID();
        String extension = extensionFor(contentType);
        String storageKey = "listing-images/" + ownerId + "/" + token + extension;
        UploadSession session = repository.save(UploadSession.create(
                ownerId, storageKey, filename.trim(), contentType.toLowerCase(Locale.ROOT), fileSize, expiresAt, now
        ));
        ObjectStoragePort.PresignedUpload upload = objectStorage.presignUpload(
                storageKey, session.getContentType(), fileSize, expiresAt
        );
        return new UploadView.Presigned(session.getId(), storageKey, upload.uploadUrl(), upload.expiresAt());
    }

    @Transactional
    public UploadView.Completed complete(UUID ownerId, UUID uploadSessionId) {
        UploadSession session = ownedSession(ownerId, uploadSessionId);
        ObjectStoragePort.StoredObjectMetadata metadata;
        try {
            metadata = objectStorage.head(session.getStorageKey());
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "업로드된 파일을 확인할 수 없습니다.");
        }
        if (!hasValidSignature(session.getContentType(), metadata.signaturePrefix())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "파일 내용이 이미지 형식과 일치하지 않습니다.");
        }
        session.verify(metadata.contentType(), metadata.fileSize(), clock.instant());
        return new UploadView.Completed(
                session.getId(), session.getStorageKey(), objectStorage.publicUrl(session.getStorageKey())
        );
    }

    @Transactional
    public List<UploadView.Attachment> attachUploads(UUID ownerId, List<UUID> uploadSessionIds) {
        if (uploadSessionIds == null || uploadSessionIds.size() < 2 || uploadSessionIds.size() > 6) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "매물 이미지는 2장 이상 6장 이하이어야 합니다.");
        }
        if (uploadSessionIds.stream().anyMatch(Objects::isNull)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "업로드 세션 ID가 비어 있습니다.");
        }
        if (Set.copyOf(uploadSessionIds).size() != uploadSessionIds.size()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "중복된 업로드 세션이 있습니다.");
        }
        Map<UUID, UploadSession> sessions = new HashMap<>();
        repository.findAllById(uploadSessionIds).forEach(session -> sessions.put(session.getId(), session));
        if (sessions.size() != uploadSessionIds.size()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "업로드 세션을 찾을 수 없습니다.");
        }
        Instant now = clock.instant();
        List<UploadView.Attachment> attachments = new ArrayList<>(uploadSessionIds.size());
        for (UUID id : uploadSessionIds) {
            UploadSession session = sessions.get(id);
            session.attach(ownerId, now);
            attachments.add(new UploadView.Attachment(
                    session.getId(), session.getStorageKey(), objectStorage.publicUrl(session.getStorageKey())
            ));
        }
        return List.copyOf(attachments);
    }

    @Transactional
    public int expirePending() {
        return expirePending(200);
    }

    @Transactional
    public int expirePending(int requestedLimit) {
        Instant now = clock.instant();
        int limit = Math.clamp(requestedLimit, 1, 500);
        List<UploadSession> expired = repository.lockExpiredPending(now, limit);
        expired.forEach(session -> {
            session.expire(now);
            objectStorage.delete(session.getStorageKey());
        });
        return expired.size();
    }

    private UploadSession ownedSession(UUID ownerId, UUID sessionId) {
        UploadSession session = repository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "업로드 세션을 찾을 수 없습니다."));
        if (!session.getOwnerId().equals(ownerId)) throw new BusinessException(ErrorCode.FORBIDDEN);
        return session;
    }

    private static void validate(String filename, String contentType, long fileSize) {
        if (filename == null || filename.isBlank() || filename.length() > 255) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "파일명이 올바르지 않습니다.");
        }
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "JPG, PNG, WEBP 이미지만 업로드할 수 있습니다.");
        }
        if (fileSize <= 0 || fileSize > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "파일은 10MB 이하여야 합니다.");
        }
    }

    private static String extensionFor(String contentType) {
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR, "지원하지 않는 이미지 형식입니다.");
        };
    }

    private static boolean hasValidSignature(String contentType, byte[] bytes) {
        if (bytes == null) return false;
        if ("image/jpeg".equals(contentType)) {
            return bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8
                    && (bytes[2] & 0xFF) == 0xFF;
        }
        if ("image/png".equals(contentType)) {
            byte[] signature = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
            if (bytes.length < signature.length) return false;
            for (int index = 0; index < signature.length; index++) {
                if (bytes[index] != signature[index]) return false;
            }
            return true;
        }
        return "image/webp".equals(contentType) && bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
    }
}
