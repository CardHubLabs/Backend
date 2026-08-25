package com.kant.cardhub.cart.api;

import com.kant.cardhub.cart.application.CartService;
import com.kant.cardhub.cart.application.CartView;
import com.kant.cardhub.common.api.ApiResponse;
import com.kant.cardhub.common.security.CurrentUserId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CartView.Item> add(
            @CurrentUserId UUID userId,
            @Valid @RequestBody AddCartItemRequest request
    ) {
        return ApiResponse.success(cartService.add(userId, request.listingId()));
    }

    @GetMapping
    public ApiResponse<CartView> get(@CurrentUserId UUID userId) {
        return ApiResponse.success(cartService.get(userId));
    }

    @DeleteMapping("/items/{cartItemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@CurrentUserId UUID userId, @PathVariable UUID cartItemId) {
        cartService.remove(userId, cartItemId);
    }

    public record AddCartItemRequest(@NotNull UUID listingId) {}
}
