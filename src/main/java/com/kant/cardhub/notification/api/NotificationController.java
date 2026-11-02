package com.kant.cardhub.notification.api;

import com.kant.cardhub.common.api.ApiResponse;
import com.kant.cardhub.common.security.CurrentUserId;
import com.kant.cardhub.notification.application.NotificationService;
import com.kant.cardhub.notification.domain.Notification;
import com.kant.cardhub.notification.domain.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ApiResponse<NotificationPageResponse> list(
            @CurrentUserId UUID userId,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(NotificationPageResponse.from(
                notificationService.list(userId, unreadOnly, page, size)
        ));
    }

    @PostMapping("/{notificationId}/read")
    public ApiResponse<Void> markRead(
            @CurrentUserId UUID userId,
            @PathVariable UUID notificationId
    ) {
        notificationService.markRead(userId, notificationId);
        return ApiResponse.empty();
    }

    @PostMapping("/read-all")
    public ApiResponse<ReadAllResponse> markAllRead(@CurrentUserId UUID userId) {
        return ApiResponse.success(new ReadAllResponse(notificationService.markAllRead(userId)));
    }

    public record NotificationResponse(
            UUID id,
            NotificationType type,
            String title,
            String message,
            String referenceType,
            UUID referenceId,
            Instant readAt,
            Instant createdAt
    ) {
        static NotificationResponse from(Notification notification) {
            return new NotificationResponse(
                    notification.getId(),
                    notification.getType(),
                    notification.getTitle(),
                    notification.getMessage(),
                    notification.getReferenceType(),
                    notification.getReferenceId(),
                    notification.getReadAt(),
                    notification.getCreatedAt()
            );
        }
    }

    public record NotificationPageResponse(
            List<NotificationResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
        static NotificationPageResponse from(Page<Notification> page) {
            return new NotificationPageResponse(
                    page.getContent().stream().map(NotificationResponse::from).toList(),
                    page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages()
            );
        }
    }

    public record ReadAllResponse(int updatedCount) {
    }
}

