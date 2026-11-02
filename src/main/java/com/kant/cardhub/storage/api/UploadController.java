package com.kant.cardhub.storage.api;

import com.kant.cardhub.common.api.ApiResponse;
import com.kant.cardhub.common.security.CurrentUserId;
import com.kant.cardhub.storage.application.UploadService;
import com.kant.cardhub.storage.application.UploadView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/uploads")
public class UploadController {

    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping("/presign")
    public ApiResponse<UploadView.Presigned> presign(
            @CurrentUserId UUID userId,
            @Valid @RequestBody PresignRequest request
    ) {
        return ApiResponse.success(uploadService.presign(
                userId, request.filename(), request.contentType(), request.fileSize()
        ));
    }

    @PostMapping("/{uploadSessionId}/complete")
    public ApiResponse<UploadView.Completed> complete(
            @CurrentUserId UUID userId,
            @PathVariable UUID uploadSessionId
    ) {
        return ApiResponse.success(uploadService.complete(userId, uploadSessionId));
    }

    public record PresignRequest(
            @NotBlank @Size(max = 255) String filename,
            @NotBlank @Size(max = 100) String contentType,
            @Min(1) @Max(10 * 1024 * 1024) long fileSize
    ) {}
}
