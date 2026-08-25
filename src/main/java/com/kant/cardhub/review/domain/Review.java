package com.kant.cardhub.review.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity
@Table(name = "reviews")
public class Review {

    private static final Pattern TAG = Pattern.compile("[A-Z0-9_]{1,40}");

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(name = "buyer_id", nullable = false)
    private UUID buyerId;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Column(nullable = false)
    private int rating;

    @Column(name = "tags_json", nullable = false, columnDefinition = "text")
    private String tagsJson;

    @Column(length = 1000)
    private String content;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Review() {
    }

    public static Review create(
            UUID orderId,
            UUID buyerId,
            UUID sellerId,
            int rating,
            List<String> tags,
            String content,
            Instant now
    ) {
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }
        List<String> safeTags = tags == null ? List.of() : tags.stream().distinct().toList();
        if (safeTags.size() > 10 || safeTags.stream().anyMatch(tag -> !TAG.matcher(tag).matches())) {
            throw new IllegalArgumentException("Review tags are invalid");
        }
        if (content != null && content.length() > 1000) {
            throw new IllegalArgumentException("Review content must not exceed 1000 characters");
        }
        Review review = new Review();
        review.id = UUID.randomUUID();
        review.orderId = orderId;
        review.buyerId = buyerId;
        review.sellerId = sellerId;
        review.rating = rating;
        review.tagsJson = encodeTags(safeTags);
        review.content = content;
        review.createdAt = now;
        review.updatedAt = now;
        return review;
    }

    private static String encodeTags(List<String> tags) {
        return tags.stream().map(tag -> "\"" + tag + "\"")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    public List<String> getTags() {
        if (tagsJson.length() <= 2) {
            return List.of();
        }
        return java.util.Arrays.stream(tagsJson.substring(1, tagsJson.length() - 1).split(","))
                .map(value -> value.substring(1, value.length() - 1))
                .toList();
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public UUID getBuyerId() { return buyerId; }
    public UUID getSellerId() { return sellerId; }
    public int getRating() { return rating; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
}
