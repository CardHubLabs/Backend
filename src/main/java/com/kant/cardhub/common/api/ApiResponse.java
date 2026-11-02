package com.kant.cardhub.common.api;

public record ApiResponse<T>(T data, ApiMeta meta) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(data, ApiMeta.now());
    }

    public static ApiResponse<Void> empty() {
        return new ApiResponse<>(null, ApiMeta.now());
    }
}
