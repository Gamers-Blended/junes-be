package com.gamersblended.junes.service;

import com.gamersblended.junes.dto.CartItemDTO;
import com.gamersblended.junes.dto.ProductInCartDTO;
import com.gamersblended.junes.exception.InvalidQuantityException;
import com.gamersblended.junes.exception.MissingIdentifierException;
import com.gamersblended.junes.model.Cart;
import com.gamersblended.junes.model.CartItem;
import com.gamersblended.junes.model.Product;
import com.gamersblended.junes.repository.RedisCartRepository;
import com.gamersblended.junes.repository.jpa.CartDatabaseRepository;
import com.gamersblended.junes.repository.jpa.CartRepository;
import com.gamersblended.junes.repository.mongodb.ProductRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
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
public class CartService {

    private static final String UNKNOWN_PRODUCT = "Unknown product";
    private final RedisCartRepository redisCartRepository;
    private final CartRepository cartRepository;
    private final CartDatabaseRepository cartDatabaseRepository; // For async persistence
    private final ProductRepository productRepository;

    public CartService(RedisCartRepository redisCartRepository, CartRepository cartRepository, CartDatabaseRepository cartDatabaseRepository, ProductRepository productRepository) {
        this.redisCartRepository = redisCartRepository;
        this.cartRepository = cartRepository;
        this.cartDatabaseRepository = cartDatabaseRepository;
        this.productRepository = productRepository;
    }

    public Cart getOrCreateCart(UUID userID, UUID sessionID) {
        Optional<Cart> cart = redisCartRepository.getCart(userID, sessionID);
        return cart.orElseGet(() -> redisCartRepository.createCart(userID, sessionID));
    }

    public void addItemToCart(UUID userID, UUID sessionID, CartItemDTO cartItemDTO) {
        // Validate quantity
        if (Boolean.FALSE.equals(validateQuantity(cartItemDTO.getQuantity()))) {
            throw new InvalidQuantityException("Error in adding to cart due to invalid quantity value: " + cartItemDTO.getQuantity());
        }

        productRepository.findById(cartItemDTO.getProductID())
                .orElseThrow(() -> {
                    log.error("Product ID not found: {}", cartItemDTO.getProductID());
                    return new ProductNotFoundException("Transaction not found");
                });

        boolean success = redisCartRepository.addItem(userID, sessionID, cartItemDTO);

        if (success) {
            asyncPersistToDatabase(userID, sessionID);
        }
    }

    public void removeItemFromCart(UUID userID, UUID sessionID, String productID) {
        boolean success = redisCartRepository.removeItem(userID, sessionID, productID);

        if (success) {
            asyncPersistToDatabase(userID, sessionID);
        }
    }

    public void updateItemQuantity(UUID userID, UUID sessionID, String productID, int quantity) {
        if (userID == null && sessionID == null) {
            throw new MissingIdentifierException("User ID or Session ID required");
        }

        if (Boolean.FALSE.equals(validateQuantity(quantity))) {
            throw new InvalidQuantityException("Error in updating quantity due to invalid quantity value: " + quantity);
        }

        boolean success = redisCartRepository.updateItemQuantity(userID, sessionID, productID, quantity);

        if (success) {
            asyncPersistToDatabase(userID, sessionID);
        }
    }

    public boolean clearCart(UUID userID, UUID sessionID) {
        boolean success = redisCartRepository.clearCart(userID, sessionID);

        if (success) {
            asyncPersistToDatabase(userID, sessionID);
        }

        return success;
    }

    public boolean deleteCart(UUID userID, UUID sessionID) {
        return redisCartRepository.deleteCart(userID, sessionID);
    }

    @Async
    @Transactional
    public void asyncPersistToDatabase(UUID userID, UUID sessionID) {
        // Only persist registered user carts to database
        if (null == userID) {
            return;
        }

        try {
            syncCartFromRedis(userID, sessionID);

            log.info("Async persisted cart to database for userID = {}", userID);
        } catch (Exception ex) {
            log.error("Error persisting cart to database for userID = {}", userID, ex);
        }
    }

    public void syncCartFromRedis(UUID userID, UUID sessionID) {
        Optional<Cart> redisCart = redisCartRepository.getCart(userID, sessionID);

        redisCart.ifPresent(rCart -> {
            Cart dbCart = cartDatabaseRepository.findByUserID(userID)
                    .orElse(new Cart());

            dbCart.setUserID(userID);
            dbCart.setSessionID(sessionID);

            dbCart.getItemList().clear();
            rCart.getItemList().forEach(dbCart::addItem);

            cartDatabaseRepository.save(dbCart);

        });
    }

    public Page<ProductInCartDTO> getCartProducts(UUID userID, UUID sessionID, Pageable pageable) {
        Cart cart = getOrCreateCart(userID, sessionID);
        log.info("userID {} has {} item(s) in cart.", userID, cart.getItemList().size());

        return generateCartPage(cart, pageable);
    }

    public Page<ProductInCartDTO> generateCartPage(Cart cart, Pageable pageable) {

        if (cart.getItemList().isEmpty()) {
            return Page.empty(pageable);
        }
        // Extract productIDs to fetch metadata
        List<String> productIDFromCartList = cart.getItemList().stream()
                .map(CartItem::getProductID)
                .toList();

        // Fetch product metadata from product database
        List<Product> metadataList = productRepository.findByIdIn(productIDFromCartList);
        Map<String, Product> productMap = metadataList.stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        // Create DTO using cart items and metadata data
        List<ProductInCartDTO> productsInCartList = cart.getItemList().stream()
                .map(currentProductInCartItem -> {
                    Product metadata = productMap.get(currentProductInCartItem.getProductID());
                    if (metadata != null) {
                        return new ProductInCartDTO(
                                currentProductInCartItem.getProductID(),
                                metadata.getName(),
                                metadata.getSlug(),
                                metadata.getPrice(),
                                metadata.getPlatform(),
                                metadata.getRegion(),
                                metadata.getEdition(),
                                metadata.getProductImageUrl(),
                                currentProductInCartItem.getQuantity(),
                                currentProductInCartItem.getCreatedOn()
                        );
                    } else {
                        // Case when productID not found in MongoDB
                        return new ProductInCartDTO(
                                currentProductInCartItem.getProductID(),
                                UNKNOWN_PRODUCT,
                                "",
                                new BigDecimal("0.00"),
                                UNKNOWN_PRODUCT,
                                UNKNOWN_PRODUCT,
                                UNKNOWN_PRODUCT,
                                "",
                                currentProductInCartItem.getQuantity(),
                                currentProductInCartItem.getCreatedOn()
                        );
                    }
                })
                .toList();

        return new PageImpl<>(productsInCartList, pageable, cart.getItemList().size());
    }

    /**
     * Check that quantity is at least 1
     *
     * @param quantity Integer value to check
     * @return True if value >= 0, else false
     */
    public Boolean validateQuantity(Integer quantity) {
        if (quantity <= 0) {
            log.error("Invalid quantity value {} given.", quantity);
            return false;
        }
        return true;
    }
}
