package com.kant.cardhub.user.api;

import com.kant.cardhub.common.api.ApiResponse;
import com.kant.cardhub.common.security.CurrentUserId;
import com.kant.cardhub.user.application.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    ApiResponse<MeResponse> get(@CurrentUserId UUID userId) {
        return ApiResponse.success(MeResponse.from(userService.get(userId)));
    }

    @PatchMapping
    ApiResponse<MeResponse> update(
            @CurrentUserId UUID userId,
            @Valid @RequestBody UpdateMeRequest request
    ) {
        return ApiResponse.success(MeResponse.from(userService.update(userId, request)));
    }

    @DeleteMapping
    ResponseEntity<ApiResponse<Void>> withdraw(@CurrentUserId UUID userId) {
        userService.withdraw(userId);
        return ResponseEntity.ok(ApiResponse.empty());
    }
}
