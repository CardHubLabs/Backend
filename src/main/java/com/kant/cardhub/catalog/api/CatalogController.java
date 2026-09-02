package com.kant.cardhub.catalog.api;

import com.kant.cardhub.catalog.application.CatalogService;
import com.kant.cardhub.catalog.application.CatalogView;
import com.kant.cardhub.catalog.domain.GameCode;
import com.kant.cardhub.catalog.domain.PrintType;
import com.kant.cardhub.catalog.domain.VersionType;
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
import org.springframework.web.bind.annotation.PatchMapping;
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

    @GetMapping("/catalog/variants")
    public ApiResponse<CatalogView.Page<CatalogView.Variant>> variants(
            @RequestParam(required = false) UUID gameId,
            @RequestParam(required = false) GameCode gameCode,
            @RequestParam(required = false) UUID setId,
            @RequestParam(required = false) UUID cardId,
            @RequestParam(required = false) @Size(max = 30) String language,
            @RequestParam(required = false) VersionType versionType,
            @RequestParam(required = false) PrintType printType,
            @RequestParam(defaultValue = "true") Boolean isListable,
            @RequestParam(required = false) @Size(max = 100) String rarity,
            @RequestParam(required = false) @Size(max = 100) String legacyVariantRarity,
            @RequestParam(required = false) @Size(max = 100) String query,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.success(catalogService.variants(
                gameId, gameCode, setId, cardId, language, versionType, printType, isListable,
                rarity, legacyVariantRarity, query, page, size
        ));
    }

    @PostMapping("/admin/catalog/games")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CatalogView.Game> createGame(@Valid @RequestBody CreateGameRequest request) {
        GameCode code = request.code() == null ? GameCode.fromLegacySlug(request.slug()) : request.code();
        return ApiResponse.success(catalogService.createGame(request.name(), request.slug(), code));
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
        return ApiResponse.success(catalogService.createCard(
                request.setId(), request.name(), request.cardNumber(),
                request.nameKo(), request.nameEn(), request.nameJa(), request.rarity()
        ));
    }

    @PostMapping("/admin/catalog/variants")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CatalogView.Variant> createVariant(@Valid @RequestBody CreateVariantRequest request) {
        return ApiResponse.success(catalogService.createVariant(
                request.cardId(), request.language(), request.version(), request.rarity(), request.imageUrl(),
                request.versionType(), request.printType(), request.isListable()
        ));
    }

    @PatchMapping("/admin/catalog/games/{gameId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CatalogView.Game> updateGame(
            @PathVariable UUID gameId,
            @Valid @RequestBody UpdateGameRequest request
    ) {
        return ApiResponse.success(catalogService.updateGame(
                gameId, request.name(), request.slug(), request.code()));
    }

    @PatchMapping("/admin/catalog/sets/{setId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CatalogView.Set> updateSet(
            @PathVariable UUID setId,
            @Valid @RequestBody UpdateSetRequest request
    ) {
        return ApiResponse.success(catalogService.updateSet(
                setId, request.gameId(), request.name(), request.code(), request.releasedAt()));
    }

    @PatchMapping("/admin/catalog/cards/{cardId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CatalogView.CardSummary> updateCard(
            @PathVariable UUID cardId,
            @Valid @RequestBody UpdateCardRequest request
    ) {
        return ApiResponse.success(catalogService.updateCard(
                cardId, request.setId(), request.name(), request.cardNumber(),
                request.nameKo(), request.nameEn(), request.nameJa(), request.rarity()));
    }

    @PatchMapping("/admin/catalog/variants/{variantId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CatalogView.Variant> updateVariant(
            @PathVariable UUID variantId,
            @Valid @RequestBody UpdateVariantRequest request
    ) {
        return ApiResponse.success(catalogService.updateVariant(
                variantId, request.language(), request.version(), request.rarity(), request.imageUrl(),
                request.versionType(), request.printType(), request.isListable()
        ));
    }

    @PatchMapping("/admin/catalog/variants/{variantId}/listability")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CatalogView.Variant> setVariantListability(
            @PathVariable UUID variantId,
            @Valid @RequestBody VariantListabilityRequest request
    ) {
        return ApiResponse.success(catalogService.setVariantListable(variantId, request.isListable()));
    }

    public record CreateGameRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 100) @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") String slug,
            GameCode code
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
            @NotBlank @Size(max = 50) String cardNumber,
            @Size(max = 200) String nameKo,
            @Size(max = 200) String nameEn,
            @Size(max = 200) String nameJa,
            @Size(max = 100) String rarity
    ) {}

    public record CreateVariantRequest(
            @NotNull UUID cardId,
            @NotBlank @Size(max = 30) String language,
            @NotBlank @Size(max = 100) String version,
            @Size(max = 100) String rarity,
            @Size(max = 1000) String imageUrl,
            VersionType versionType,
            PrintType printType,
            Boolean isListable
    ) {}

    public record UpdateVariantRequest(
            @NotBlank @Size(max = 30) String language,
            @NotBlank @Size(max = 100) String version,
            @Size(max = 100) String rarity,
            @Size(max = 1000) String imageUrl,
            @NotNull VersionType versionType,
            @NotNull PrintType printType,
            @NotNull Boolean isListable
    ) {}

    public record VariantListabilityRequest(@NotNull Boolean isListable) {}

    public record UpdateGameRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 100) @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") String slug,
            @NotNull GameCode code
    ) {}

    public record UpdateSetRequest(
            @NotNull UUID gameId,
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 50) String code,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate releasedAt
    ) {}

    public record UpdateCardRequest(
            @NotNull UUID setId,
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 50) String cardNumber,
            @Size(max = 200) String nameKo,
            @Size(max = 200) String nameEn,
            @Size(max = 200) String nameJa,
            @Size(max = 100) String rarity
    ) {}
}
