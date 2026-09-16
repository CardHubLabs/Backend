package com.kant.cardhub.listing.application;

import com.kant.cardhub.catalog.application.CatalogService;
import com.kant.cardhub.common.error.BusinessException;
import com.kant.cardhub.common.error.ErrorCode;
import com.kant.cardhub.listing.domain.CardCondition;
import com.kant.cardhub.listing.domain.Listing;
import com.kant.cardhub.listing.domain.ListingImage;
import com.kant.cardhub.listing.domain.ListingModerationStatus;
import com.kant.cardhub.listing.domain.ListingStatus;
import com.kant.cardhub.listing.infrastructure.ListingImageRepository;
import com.kant.cardhub.listing.infrastructure.ListingRepository;
import com.kant.cardhub.storage.application.ObjectStoragePort;
import com.kant.cardhub.storage.application.UploadService;
import com.kant.cardhub.storage.application.UploadView;
import com.kant.cardhub.user.application.ActiveUserLock;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ListingService {

    private final ListingRepository listingRepository;
    private final ListingImageRepository imageRepository;
    private final CatalogService catalogService;
    private final UploadService uploadService;
    private final ObjectStoragePort objectStorage;
    private final Clock clock;
    private final List<ListingEventPort> eventPorts;
    private final ActiveUserLock activeUserLock;

    public ListingService(
            ListingRepository listingRepository,
            ListingImageRepository imageRepository,
            CatalogService catalogService,
            UploadService uploadService,
            ObjectStoragePort objectStorage,
            Clock clock,
            List<ListingEventPort> eventPorts,
            ActiveUserLock activeUserLock
    ) {
        this.listingRepository = listingRepository;
        this.imageRepository = imageRepository;
        this.catalogService = catalogService;
        this.uploadService = uploadService;
        this.objectStorage = objectStorage;
        this.clock = clock;
        this.eventPorts = List.copyOf(eventPorts);
        this.activeUserLock = activeUserLock;
    }

    public ListingView.Page<ListingView.Summary> search(
            UUID cardVariantId,
            Long minPrice,
            Long maxPrice,
            String sort,
            int page,
            int size
    ) {
        validateRange(minPrice, maxPrice);
        var result = listingRepository.search(
                ListingStatus.ACTIVE,
                ListingModerationStatus.CLEAR,
                cardVariantId,
                minPrice,
                maxPrice,
                PageRequest.of(page, size, publicSort(sort))
        );
        Map<UUID, ListingImage> primaryImages = primaryImages(result.getContent());
        List<ListingView.Summary> content = result.getContent().stream()
                .map(listing -> toSummary(listing, primaryImages.get(listing.getId())))
                .toList();
        return new ListingView.Page<>(
                content, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages()
        );
    }

    public ListingView.Detail detail(UUID listingId) {
        Listing listing = find(listingId);
        if (listing.isBlocked()
                || listing.getStatus() == ListingStatus.DELETED
                || listing.getStatus() == ListingStatus.HIDDEN) {
            throw new BusinessException(ErrorCode.LISTING_NOT_FOUND);
        }
        return toDetail(listing);
    }

    public ListingView.Page<ListingView.Summary> mine(UUID sellerId, int page, int size) {
        var result = listingRepository.findAllBySellerIdAndStatusNot(
                sellerId,
                ListingStatus.DELETED,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        Map<UUID, ListingImage> primaryImages = primaryImages(result.getContent());
        return new ListingView.Page<>(
                result.getContent().stream().map(item -> toSummary(item, primaryImages.get(item.getId()))).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages()
        );
    }

    @Transactional
    public ListingView.Detail create(
            UUID sellerId,
            UUID cardVariantId,
            CardCondition condition,
            String description,
            long price,
            long shippingFee,
            List<UUID> uploadSessionIds
    ) {
        activeUserLock.requireActive(sellerId);
        catalogService.requireListable(cardVariantId);
        List<UploadView.Attachment> attachments = uploadService.attachUploads(sellerId, uploadSessionIds);
        Instant now = clock.instant();
        Listing listing = listingRepository.save(Listing.create(
                sellerId, cardVariantId, condition, description, price, shippingFee, now
        ));
        List<ListingImage> images = new ArrayList<>(attachments.size());
        for (int index = 0; index < attachments.size(); index++) {
            UploadView.Attachment attachment = attachments.get(index);
            images.add(ListingImage.create(
                    listing.getId(), attachment.uploadSessionId(), attachment.storageKey(), index, now
            ));
        }
        imageRepository.saveAll(images);
        eventPorts.forEach(port -> port.onPublished(
                listing.getId(), listing.getCardVariantId(), listing.getSellerId(), listing.getPrice()
        ));
        return toDetail(listing, images);
    }

    @Transactional
    public ListingView.Detail update(
            UUID sellerId,
            UUID listingId,
            CardCondition condition,
            String description,
            long price,
            long shippingFee
    ) {
        activeUserLock.requireActive(sellerId);
        Listing listing = find(listingId);
        long previousPrice = listing.getPrice();
        listing.update(sellerId, condition, description, price, shippingFee, clock.instant());
        if (previousPrice != price) {
            eventPorts.forEach(port -> port.onPriceChanged(
                    listing.getId(), listing.getCardVariantId(), listing.getSellerId(), previousPrice, price
            ));
        }
        return toDetail(listing);
    }

    @Transactional
    public ListingView.Detail hide(UUID sellerId, UUID listingId) {
        activeUserLock.requireActive(sellerId);
        Listing listing = find(listingId);
        listing.hide(sellerId, clock.instant());
        return toDetail(listing);
    }

    @Transactional
    public ListingView.Detail activate(UUID sellerId, UUID listingId) {
        activeUserLock.requireActive(sellerId);
        Listing listing = find(listingId);
        listing.activate(sellerId, clock.instant());
        eventPorts.forEach(port -> port.onPublished(
                listing.getId(), listing.getCardVariantId(), listing.getSellerId(), listing.getPrice()
        ));
        return toDetail(listing);
    }

    @Transactional
    public void delete(UUID sellerId, UUID listingId) {
        activeUserLock.requireActive(sellerId);
        find(listingId).delete(sellerId, clock.instant());
    }

    public Listing requirePurchasable(UUID listingId) {
        Listing listing = find(listingId);
        if (!listing.isReservable()) {
            throw new BusinessException(ErrorCode.LISTING_INVALID_STATE);
        }
        return listing;
    }

    private Listing find(UUID listingId) {
        return listingRepository.findById(listingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LISTING_NOT_FOUND));
    }

    private ListingView.Detail toDetail(Listing listing) {
        return toDetail(listing, imageRepository.findAllByListingIdOrderBySortOrderAsc(listing.getId()));
    }

    private ListingView.Detail toDetail(Listing listing, List<ListingImage> images) {
        return new ListingView.Detail(
                listing.getId(), listing.getSellerId(), listing.getCardVariantId(), listing.getCardCondition(),
                listing.getDescription(), listing.getPrice(), listing.getShippingFee(), listing.getStatus(),
                listing.getModerationStatus(), listing.getEffectiveStatus(), listing.getBlockedAt(),
                listing.getBlockedReason(),
                images.stream().map(this::toImage).toList(), listing.getCreatedAt(), listing.getUpdatedAt()
        );
    }

    private ListingView.Summary toSummary(Listing listing, ListingImage primaryImage) {
        return new ListingView.Summary(
                listing.getId(), listing.getSellerId(), listing.getCardVariantId(), listing.getCardCondition(),
                listing.getPrice(), listing.getShippingFee(), listing.getStatus(),
                listing.getModerationStatus(), listing.getEffectiveStatus(), listing.getBlockedAt(),
                listing.getBlockedReason(),
                primaryImage == null ? null : objectStorage.publicUrl(primaryImage.getStorageKey()),
                listing.getCreatedAt()
        );
    }

    private ListingView.Image toImage(ListingImage image) {
        return new ListingView.Image(
                image.getId(), objectStorage.publicUrl(image.getStorageKey()), image.getSortOrder()
        );
    }

    private Map<UUID, ListingImage> primaryImages(List<Listing> listings) {
        if (listings.isEmpty()) return Map.of();
        Map<UUID, ListingImage> result = new HashMap<>();
        imageRepository.findAllByListingIdInOrderByListingIdAscSortOrderAsc(
                listings.stream().map(Listing::getId).toList()
        ).forEach(image -> result.putIfAbsent(image.getListingId(), image));
        return result;
    }

    private static Sort publicSort(String sort) {
        if ("priceAsc".equals(sort)) return Sort.by(Sort.Direction.ASC, "price").and(Sort.by("id"));
        if ("priceDesc".equals(sort)) return Sort.by(Sort.Direction.DESC, "price").and(Sort.by("id"));
        return Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id").descending());
    }

    private static void validateRange(Long minPrice, Long maxPrice) {
        if ((minPrice != null && minPrice < 0)
                || (maxPrice != null && maxPrice < 0)
                || (minPrice != null && maxPrice != null && minPrice > maxPrice)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "가격 검색 범위가 올바르지 않습니다.");
        }
    }
}
