package com.gamersblended.junes.service;

import com.gamersblended.junes.dto.ProductInWishlistDTO;
import com.gamersblended.junes.dto.WishlistItemDTO;
import com.gamersblended.junes.exception.DatabaseInsertionException;
import com.gamersblended.junes.exception.MissingIdentifierException;
import com.gamersblended.junes.exception.ProductNotFoundException;
import com.gamersblended.junes.model.Product;
import com.gamersblended.junes.model.Wishlist;
import com.gamersblended.junes.model.WishlistItem;
import com.gamersblended.junes.repository.RedisWishlistRepository;
import com.gamersblended.junes.repository.jpa.WishlistDatabaseRepository;
import com.gamersblended.junes.repository.mongodb.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class WishlistService {

    private static final String UNKNOWN_PRODUCT = "Unknown product";
    private final RedisWishlistRepository redisWishlistRepository;
    private final ProductRepository productRepository;
    private final WishlistDatabaseRepository wishlistDatabaseRepository;
    private final WishlistService self;

    public WishlistService(RedisWishlistRepository redisWishlistRepository, WishlistDatabaseRepository wishlistDatabaseRepository, ProductRepository productRepository, @Lazy WishlistService self) {
        this.redisWishlistRepository = redisWishlistRepository;
        this.wishlistDatabaseRepository = wishlistDatabaseRepository;
        this.productRepository = productRepository;
        this.self = self;
    }

    public Wishlist getOrCreateWishlist(UUID userID, UUID sessionID) {
        if (null == userID && null == sessionID) {
            throw new MissingIdentifierException("User ID or Session ID required");
        }

        Optional<Wishlist> wishlist = redisWishlistRepository.getWishlist(userID, sessionID);
        return wishlist.orElseGet(() -> redisWishlistRepository.createWishlist(userID, sessionID));
    }

    public Page<ProductInWishlistDTO> getWishlistProducts(UUID userID, UUID sessionID, Pageable pageable) {
        Wishlist wishlist = getOrCreateWishlist(userID, sessionID);
        log.info("userID {} has {} item(s) in wishlist.", userID, wishlist.getItemList().size());

        return generateWishlistPage(wishlist, pageable);
    }

    public Page<ProductInWishlistDTO> generateWishlistPage(Wishlist wishlist, Pageable pageable) {

        if (wishlist.getItemList().isEmpty()) {
            return Page.empty(pageable);
        }
        // Extract product IDs to fetch metadata from product database
        List<ObjectId> productIDFromWishlistList = wishlist.getItemList().stream()
                .map(item -> new ObjectId(item.getProductID()))
                .toList();

        // Fetch product metadata from product database
        List<Product> metadataList = productRepository.findByIdIn(productIDFromWishlistList);
        Map<String, Product> productMap = metadataList.stream()
                .collect(Collectors.toMap(product -> product.getId().toHexString(), Function.identity()));

        List<ProductInWishlistDTO> productsInWishlistList = wishlist.getItemList().stream()
                .map(currentProductInWishlistItem -> {
                    Product metadata = productMap.get(currentProductInWishlistItem.getProductID());
                    if (null != metadata) {
                        return new ProductInWishlistDTO(
                                currentProductInWishlistItem.getProductID(),
                                metadata.getName(),
                                metadata.getSlug(),
                                metadata.getPrice(),
                                metadata.getPlatform(),
                                metadata.getRegion(),
                                metadata.getEdition(),
                                metadata.getProductImageUrl(),
                                currentProductInWishlistItem.getCreatedOn()
                        );
                    } else {
                        // productID not found in product database
                        return new ProductInWishlistDTO(
                                currentProductInWishlistItem.getProductID(),
                                UNKNOWN_PRODUCT,
                                "",
                                new BigDecimal("0.00"),
                                UNKNOWN_PRODUCT,
                                UNKNOWN_PRODUCT,
                                UNKNOWN_PRODUCT,
                                "",
                                currentProductInWishlistItem.getCreatedOn()
                        );
                    }
                })
                .toList();

        return new PageImpl<>(productsInWishlistList, pageable, wishlist.getItemList().size());
    }

    public void addItemToWishlist(UUID userID, UUID sessionID, WishlistItemDTO wishlistItemDTO) {
        validateForWishlistItems(userID, sessionID, wishlistItemDTO.getProductID());

        boolean success = redisWishlistRepository.addItem(userID, sessionID, wishlistItemDTO);

        if (success) {
            self.asyncPersistToDatabase(userID, sessionID);
        }
    }

    public void removeItemFromWishlist(UUID userID, UUID sessionID, String productID) {
        validateForWishlistItems(userID, sessionID, productID);

        boolean success = redisWishlistRepository.removeItem(userID, sessionID, productID);

        if (success) {
            self.asyncPersistToDatabase(userID, sessionID);
        }
    }

    public void clearWishlist(UUID userID, UUID sessionID) {
        if (userID == null && sessionID == null) {
            throw new MissingIdentifierException("User ID or Session ID required");
        }

        boolean success = redisWishlistRepository.clearWishlist(userID, sessionID);

        if (success) {
            self.asyncPersistToDatabase(userID, sessionID);
        }
    }

    public void validateForWishlistItems(UUID userID, UUID sessionID, String productID) {
        if (null == userID && null == sessionID) {
            throw new MissingIdentifierException("User ID or Session ID required");
        }

        productRepository.findById(new ObjectId(productID))
                .orElseThrow(() -> {
                    log.error("Product ID not found: {}", productID);
                    return new ProductNotFoundException("Product not found");
                });
    }

    @Async
    @Transactional
    public void asyncPersistToDatabase(UUID userID, UUID sessionID) {
        // Only persist registered user wishlists to database
        if (null == userID) {
            return;
        }

        try {
            syncWishlistFromRedis(userID, sessionID);

            log.info("Async persisted wishlist to database for userID = {}", userID);
        } catch (DatabaseInsertionException ex) {
            log.error("Error persisting wishlist to database for userID = {}", userID, ex);
            throw ex;
        }
    }

    public void syncWishlistFromRedis(UUID userID, UUID sessionID) {
        Optional<Wishlist> redisWishlist = redisWishlistRepository.getWishlist(userID, sessionID);

        redisWishlist.ifPresent(rWishlist -> {
            Wishlist dbWishlist = wishlistDatabaseRepository.findByUserID(userID)
                    .orElse(new Wishlist());

            dbWishlist.setUserID(userID);

            Set<String> existingProductIDSet = dbWishlist.getItemList().stream()
                    .map(WishlistItem::getProductID)
                    .collect(Collectors.toSet());

            // Remove items not in Redis wishlist
            Set<String> redisProductIDSet = rWishlist.getItemList().stream()
                    .map(WishlistItem::getProductID)
                    .collect(Collectors.toSet());
            dbWishlist.getItemList().removeIf(i -> !redisProductIDSet.contains(i.getProductID()));

            // Add items missing from the database copy
            for (WishlistItem rItem : rWishlist.getItemList()) {
                if (!existingProductIDSet.contains(rItem.getProductID())) {
                    dbWishlist.addItem(rItem);
                }
            }

            wishlistDatabaseRepository.save(dbWishlist);

        });
    }

}
