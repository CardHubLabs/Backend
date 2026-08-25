package com.kant.cardhub.catalog.api;

import com.kant.cardhub.catalog.application.CatalogService;
import com.kant.cardhub.catalog.application.CatalogView;
import com.kant.cardhub.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/catalog/games")
    public ApiResponse<List<CatalogView.Game>> games() {
        return ApiResponse.success(catalogService.games());
    }

    @GetMapping("/catalog/sets")
    public ApiResponse<List<CatalogView.Set>> sets(@RequestParam UUID gameId) {
        return ApiResponse.success(catalogService.sets(gameId));
    }

    @GetMapping("/catalog/cards")
    public ApiResponse<CatalogView.Page<CatalogView.CardSummary>> cards(
            @RequestParam(required = false) UUID setId,
            @RequestParam(required = false) @Size(max = 100) String query,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.success(catalogService.cards(setId, query, page, size));
    }

    @GetMapping("/catalog/cards/{cardId}")
    public ApiResponse<CatalogView.CardDetail> card(@PathVariable UUID cardId) {
        return ApiResponse.success(catalogService.card(cardId));
    }

    @GetMapping("/catalog/variants/{variantId}")
    public ApiResponse<CatalogView.Variant> variant(@PathVariable UUID variantId) {
        return ApiResponse.success(catalogService.variant(variantId));
    }

    @PostMapping("/admin/catalog/games")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CatalogView.Game> createGame(@Valid @RequestBody CreateGameRequest request) {
        return ApiResponse.success(catalogService.createGame(request.name(), request.slug()));
    }

    @PostMapping("/admin/catalog/sets")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CatalogView.Set> createSet(@Valid @RequestBody CreateSetRequest request) {
        return ApiResponse.success(catalogService.createSet(
                request.gameId(), request.name(), request.code(), request.releasedAt()
        ));
    }

    @PostMapping("/admin/catalog/cards")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CatalogView.CardSummary> createCard(@Valid @RequestBody CreateCardRequest request) {
        return ApiResponse.success(catalogService.createCard(request.setId(), request.name(), request.cardNumber()));
    }

    @PostMapping("/admin/catalog/variants")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CatalogView.Variant> createVariant(@Valid @RequestBody CreateVariantRequest request) {
        return ApiResponse.success(catalogService.createVariant(
                request.cardId(), request.language(), request.version(), request.rarity(), request.imageUrl()
        ));
    }

    public record CreateGameRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 100) @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") String slug
    ) {}

    public record CreateSetRequest(
            @NotNull UUID gameId,
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 50) String code,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate releasedAt
    ) {}

    public record CreateCardRequest(
            @NotNull UUID setId,
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 50) String cardNumber
    ) {}

    public record CreateVariantRequest(
            @NotNull UUID cardId,
            @NotBlank @Size(max = 30) String language,
            @NotBlank @Size(max = 100) String version,
            @Size(max = 100) String rarity,
            @Size(max = 1000) String imageUrl
    ) {}
}
