package com.gamersblended.junes.service;

import com.gamersblended.junes.dto.ProductInWishlistDTO;
import com.gamersblended.junes.exception.MissingIdentifierException;
import com.gamersblended.junes.model.Product;
import com.gamersblended.junes.model.Wishlist;
import com.gamersblended.junes.repository.RedisWishlistRepository;
import com.gamersblended.junes.repository.mongodb.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class WishlistService {

    private static final String UNKNOWN_PRODUCT = "Unknown product";
    private final RedisWishlistRepository redisWishlistRepository;
    private final ProductRepository productRepository;

    public WishlistService(RedisWishlistRepository redisWishlistRepository, ProductRepository productRepository) {
        this.redisWishlistRepository = redisWishlistRepository;
        this.productRepository = productRepository;
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
}
