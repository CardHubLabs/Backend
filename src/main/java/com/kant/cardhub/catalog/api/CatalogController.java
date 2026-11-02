package com.kant.cardhub.catalog.api;

import com.kant.cardhub.catalog.application.CatalogService;
import com.kant.cardhub.catalog.application.CatalogView;
import com.kant.cardhub.catalog.domain.GameCode;
import com.kant.cardhub.catalog.domain.PrintType;
import com.kant.cardhub.catalog.domain.VersionType;
import com.kant.cardhub.common.api.ApiResponse;
import com.kant.cardhub.common.security.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Catalog", description = "카드 게임·세트·카드·Variant 카탈로그 API")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/catalog/games")
    @SecurityRequirements
    @Operation(summary = "카드 게임 목록 조회", description = "지원하는 카드 게임을 공개 조회합니다.")
    public ApiResponse<List<CatalogView.Game>> games() {
        return ApiResponse.success(catalogService.games());
    }

    @GetMapping("/catalog/sets")
    @SecurityRequirements
    @Operation(summary = "카드 세트 목록 조회", description = "게임 ID에 속한 카드 세트를 공개 조회합니다.")
    public ApiResponse<List<CatalogView.Set>> sets(@RequestParam UUID gameId) {
        return ApiResponse.success(catalogService.sets(gameId));
    }

    @GetMapping("/catalog/cards")
    @SecurityRequirements
    @Operation(
            summary = "카드 검색",
            description = "한·일·영 이름과 카드 번호를 대상으로 카드를 공개 검색합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "카드 검색 성공", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "검색어나 페이지 조건 오류")
            }
    )
    public ApiResponse<CatalogView.Page<CatalogView.CardSummary>> cards(
            @RequestParam(required = false) UUID setId,
            @Parameter(description = "한·일·영 카드 이름 또는 카드 번호", example = "피카츄")
            @RequestParam(required = false) @Size(max = 100) String query,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.success(catalogService.cards(setId, query, page, size));
    }

    @GetMapping("/catalog/cards/{cardId}")
    @SecurityRequirements
    @Operation(
            summary = "카드 상세 조회",
            description = "카드와 연결된 Variant를 공개 조회합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "카드 상세 조회 성공", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "카드를 찾을 수 없음")
            }
    )
    public ApiResponse<CatalogView.CardDetail> card(@PathVariable UUID cardId) {
        return ApiResponse.success(catalogService.card(cardId));
    }

    @GetMapping("/catalog/variants/{variantId}")
    @SecurityRequirements
    @Operation(
            summary = "Variant 상세 조회",
            description = "언어·버전·인쇄 형태가 구분된 카드 Variant를 공개 조회합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "Variant 상세 조회 성공", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Variant를 찾을 수 없음")
            }
    )
    public ApiResponse<CatalogView.Variant> variant(@PathVariable UUID variantId) {
        return ApiResponse.success(catalogService.variant(variantId));
    }

    @GetMapping("/catalog/variants")
    @SecurityRequirements
    @Operation(
            summary = "카드 Variant 검색",
            description = "게임·세트·카드·언어·버전·인쇄 형태·등록 가능 여부로 Variant를 공개 검색합니다. "
                    + "query는 한·일·영 카드 이름과 카드 번호를 대상으로 합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "Variant 검색 성공", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "필터 또는 페이지 조건 오류")
            }
    )
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
            @Parameter(description = "한·일·영 카드 이름 또는 카드 번호", example = "피카츄")
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
    @Operation(
            summary = "관리자 게임 생성",
            description = "카드 게임을 생성하고 성공한 변경을 관리자 감사 로그에 한 번 기록합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "게임 생성 완료", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 형식 오류"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "게임 코드 또는 slug 중복")
            }
    )
    public ApiResponse<CatalogView.Game> createGame(
            @CurrentUserId UUID adminId,
            @Valid @RequestBody CreateGameRequest request
    ) {
        GameCode code = request.code() == null ? GameCode.fromLegacySlug(request.slug()) : request.code();
        return ApiResponse.success(catalogService.createGame(adminId, request.name(), request.slug(), code));
    }

    @PostMapping("/admin/catalog/sets")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "관리자 세트 생성",
            description = "게임에 카드 세트를 생성하고 성공한 변경을 관리자 감사 로그에 한 번 기록합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "세트 생성 완료", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 형식 오류"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "게임을 찾을 수 없음"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "세트 코드 중복")
            }
    )
    public ApiResponse<CatalogView.Set> createSet(
            @CurrentUserId UUID adminId,
            @Valid @RequestBody CreateSetRequest request
    ) {
        return ApiResponse.success(catalogService.createSet(
                adminId, request.gameId(), request.name(), request.code(), request.releasedAt()
        ));
    }

    @PostMapping("/admin/catalog/cards")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "관리자 카드 생성",
            description = "세트에 다국어 이름을 포함한 카드를 생성하고 관리자 감사 로그를 기록합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "카드 생성 완료", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 형식 오류"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "세트를 찾을 수 없음"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "카드 번호 중복")
            }
    )
    public ApiResponse<CatalogView.CardSummary> createCard(
            @CurrentUserId UUID adminId,
            @Valid @RequestBody CreateCardRequest request
    ) {
        return ApiResponse.success(catalogService.createCard(
                adminId, request.setId(), request.name(), request.cardNumber(),
                request.nameKo(), request.nameEn(), request.nameJa(), request.rarity()
        ));
    }

    @PostMapping("/admin/catalog/variants")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "관리자 Variant 생성",
            description = "카드의 언어·에디션·인쇄 형태 Variant를 생성하고 관리자 감사 로그를 기록합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "Variant 생성 완료", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 형식 오류"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "카드를 찾을 수 없음"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "동일 Variant 중복")
            }
    )
    public ApiResponse<CatalogView.Variant> createVariant(
            @CurrentUserId UUID adminId,
            @Valid @RequestBody CreateVariantRequest request
    ) {
        return ApiResponse.success(catalogService.createVariant(
                adminId, request.cardId(), request.language(), request.version(), request.rarity(), request.imageUrl(),
                request.versionType(), request.printType(), request.isListable()
        ));
    }

    @PatchMapping("/admin/catalog/games/{gameId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "관리자 게임 수정",
            description = "게임 메타데이터를 수정하고 성공한 변경을 관리자 감사 로그에 기록합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "게임 수정 완료", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "게임을 찾을 수 없음"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "코드 또는 slug 중복")
            }
    )
    public ApiResponse<CatalogView.Game> updateGame(
            @CurrentUserId UUID adminId,
            @PathVariable UUID gameId,
            @Valid @RequestBody UpdateGameRequest request
    ) {
        return ApiResponse.success(catalogService.updateGame(
                adminId, gameId, request.name(), request.slug(), request.code()));
    }

    @PatchMapping("/admin/catalog/sets/{setId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "관리자 세트 수정",
            description = "세트 메타데이터를 수정하고 성공한 변경을 관리자 감사 로그에 기록합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "세트 수정 완료", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "게임 또는 세트를 찾을 수 없음"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "세트 코드 중복")
            }
    )
    public ApiResponse<CatalogView.Set> updateSet(
            @CurrentUserId UUID adminId,
            @PathVariable UUID setId,
            @Valid @RequestBody UpdateSetRequest request
    ) {
        return ApiResponse.success(catalogService.updateSet(
                adminId, setId, request.gameId(), request.name(), request.code(), request.releasedAt()));
    }

    @PatchMapping("/admin/catalog/cards/{cardId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "관리자 카드 수정",
            description = "카드와 다국어 이름을 수정하고 성공한 변경을 관리자 감사 로그에 기록합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "카드 수정 완료", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "세트 또는 카드를 찾을 수 없음"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "카드 번호 중복")
            }
    )
    public ApiResponse<CatalogView.CardSummary> updateCard(
            @CurrentUserId UUID adminId,
            @PathVariable UUID cardId,
            @Valid @RequestBody UpdateCardRequest request
    ) {
        return ApiResponse.success(catalogService.updateCard(
                adminId, cardId, request.setId(), request.name(), request.cardNumber(),
                request.nameKo(), request.nameEn(), request.nameJa(), request.rarity()));
    }

    @PatchMapping("/admin/catalog/variants/{variantId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "관리자 Variant 수정",
            description = "Variant 메타데이터와 등록 가능 여부를 수정하고 관리자 감사 로그를 기록합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "Variant 수정 완료", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Variant를 찾을 수 없음"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "동일 Variant 중복")
            }
    )
    public ApiResponse<CatalogView.Variant> updateVariant(
            @CurrentUserId UUID adminId,
            @PathVariable UUID variantId,
            @Valid @RequestBody UpdateVariantRequest request
    ) {
        return ApiResponse.success(catalogService.updateVariant(
                adminId, variantId, request.language(), request.version(), request.rarity(), request.imageUrl(),
                request.versionType(), request.printType(), request.isListable()
        ));
    }

    @PatchMapping("/admin/catalog/variants/{variantId}/listability")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "관리자 Variant 등록 가능 여부 변경",
            description = "매물 등록 가능 여부를 변경하고 최종 listable 값과 함께 관리자 감사 로그를 기록합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "등록 가능 여부 변경 완료", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Variant를 찾을 수 없음")
            }
    )
    public ApiResponse<CatalogView.Variant> setVariantListability(
            @CurrentUserId UUID adminId,
            @PathVariable UUID variantId,
            @Valid @RequestBody VariantListabilityRequest request
    ) {
        return ApiResponse.success(catalogService.setVariantListable(adminId, variantId, request.isListable()));
    }

    @Schema(
            description = "카드 게임 생성 요청",
            example = "{\"name\":\"Pokémon Card Game\",\"slug\":\"pokemon\",\"code\":\"POKEMON\"}"
    )
    public record CreateGameRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 100) @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") String slug,
            GameCode code
    ) {}

    @Schema(
            description = "카드 세트 생성 요청",
            example = "{\"gameId\":\"84e63ce6-7be0-4201-92cf-e4e9a641471c\",\"name\":\"Scarlet & Violet\",\"code\":\"SV1\",\"releasedAt\":\"2026-01-15\"}"
    )
    public record CreateSetRequest(
            @NotNull UUID gameId,
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 50) String code,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate releasedAt
    ) {}

    @Schema(
            description = "카드 생성 요청",
            example = "{\"setId\":\"30594fb2-eb40-4c02-9ea0-2fdd93717bbb\",\"name\":\"Pikachu\",\"cardNumber\":\"025/165\",\"nameKo\":\"피카츄\",\"nameEn\":\"Pikachu\",\"nameJa\":\"ピカチュウ\",\"rarity\":\"R\"}"
    )
    public record CreateCardRequest(
            @NotNull UUID setId,
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 50) String cardNumber,
            @Size(max = 200) String nameKo,
            @Size(max = 200) String nameEn,
            @Size(max = 200) String nameJa,
            @Size(max = 100) String rarity
    ) {}

    @Schema(
            description = "카드 Variant 생성 요청",
            example = "{\"cardId\":\"3d5d861e-6811-4380-b8c7-a90c3c0ba655\",\"language\":\"KO\",\"version\":\"First Edition\",\"versionType\":\"NORMAL\",\"printType\":\"HOLO\",\"isListable\":true}"
    )
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

    @Schema(
            description = "카드 Variant 수정 요청",
            example = "{\"language\":\"KO\",\"version\":\"First Edition\",\"versionType\":\"NORMAL\",\"printType\":\"HOLO\",\"isListable\":true}"
    )
    public record UpdateVariantRequest(
            @NotBlank @Size(max = 30) String language,
            @NotBlank @Size(max = 100) String version,
            @Size(max = 100) String rarity,
            @Size(max = 1000) String imageUrl,
            @NotNull VersionType versionType,
            @NotNull PrintType printType,
            @NotNull Boolean isListable
    ) {}

    @Schema(description = "Variant 등록 가능 여부 변경 요청", example = "{\"isListable\":false}")
    public record VariantListabilityRequest(@NotNull Boolean isListable) {}

    @Schema(
            description = "카드 게임 수정 요청",
            example = "{\"name\":\"Pokémon Card Game\",\"slug\":\"pokemon\",\"code\":\"POKEMON\"}"
    )
    public record UpdateGameRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 100) @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") String slug,
            @NotNull GameCode code
    ) {}

    @Schema(
            description = "카드 세트 수정 요청",
            example = "{\"gameId\":\"84e63ce6-7be0-4201-92cf-e4e9a641471c\",\"name\":\"Scarlet & Violet\",\"code\":\"SV1\",\"releasedAt\":\"2026-01-15\"}"
    )
    public record UpdateSetRequest(
            @NotNull UUID gameId,
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 50) String code,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate releasedAt
    ) {}

    @Schema(
            description = "카드 수정 요청",
            example = "{\"setId\":\"30594fb2-eb40-4c02-9ea0-2fdd93717bbb\",\"name\":\"Pikachu\",\"cardNumber\":\"025/165\",\"nameKo\":\"피카츄\",\"nameEn\":\"Pikachu\",\"nameJa\":\"ピカチュウ\",\"rarity\":\"R\"}"
    )
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
