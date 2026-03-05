package com.gamersblended.junes.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamersblended.junes.dto.CartItemDTO;
import com.gamersblended.junes.mapper.CartProductMapper;
import com.gamersblended.junes.model.Cart;
import com.gamersblended.junes.model.CartItem;
import com.gamersblended.junes.repository.mongodb.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Repository
public class RedisCartRepository {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final CartProductMapper cartProductMapper;
    private final ProductRepository productRepository;

    public RedisCartRepository(RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper, CartProductMapper cartProductMapper, ProductRepository productRepository) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.cartProductMapper = cartProductMapper;
        this.productRepository = productRepository;
    }

    private static final String USER_CART_PREFIX = "user:cart:";
    private static final String GUEST_CART_PREFIX = "cart:";
    private static final Duration USER_CART_TTL = Duration.ofDays(30);
    private static final Duration GUEST_CART_TTL = Duration.ofDays(7);

    // Lua script for atomic cart update with optimistic locking
    private static final String UPDATE_CART_SCRIPT =
            """
                    local key = KEYS[1]
                    local newCartJson = ARGV[1]
                    local expectedVersion = tonumber(ARGV[2])
                    local ttl = tonumber(ARGV[3])
                    
                    local currentCartJson = redis.call('GET', key)
                    
                    if currentCartJson == false then
                      -- Cart doesn't exist, create new
                      redis.call('SET', key, newCartJson, 'EX', ttl)
                      return 1
                    end
                    
                    local currentCart = cjson.decode(currentCartJson)
                    
                    if currentCart.version ~= expectedVersion then
                      -- Version mismatch, concurrent modification
                      return 0
                    end
                    
                    -- Update cart
                    redis.call('SET', key, newCartJson, 'EX', ttl)
                    return 1
                    """;

    public Cart createCart(UUID userID, UUID sessionID) {
        Cart cart = Cart.builder()
                .cartID(UUID.randomUUID())
                .userID(userID)
                .sessionID(sessionID)
                .createdOn(LocalDateTime.now())
                .version(0)
                .build();

        saveCart(cart);
        return cart;
    }

    public Optional<Cart> getCart(UUID userID, UUID sessionID) {
        String key = buildKey(userID, sessionID);
        String cartJson = redisTemplate.opsForValue().get(key);

        if (null == cartJson) {
            return Optional.empty();
        }

        try {
            Cart cart = objectMapper.readValue(cartJson, Cart.class);
            return Optional.of(cart);
        } catch (Exception e) {
            log.error("Error deserialising cart from Redis: {}", key, e);
            return Optional.empty();
        }
    }

    public boolean deleteCart(UUID userID, UUID sessionID) {
        String key = buildKey(userID, sessionID);
        return redisTemplate.delete(key);
    }

    public boolean saveCart(Cart cart) {
        String key = buildKey(cart.getUserID(), cart.getSessionID());
        cart.setUpdatedOn(LocalDateTime.now());

        try {
            String cartJson = objectMapper.writeValueAsString(cart);
            Duration ttl = cart.getUserID() != null ? USER_CART_TTL : GUEST_CART_TTL;
            redisTemplate.opsForValue().set(key, cartJson, ttl);
            return true;
        } catch (Exception e) {
            log.error("Error deserialising cart from Redis: {}", key, e);
            return false;
        }
    }

    public boolean updateCartAtomic(Cart cart) {
        String key = buildKey(cart.getUserID(), cart.getSessionID());
        cart.setUpdatedOn(LocalDateTime.now());
        cart.setVersion(cart.getVersion() + 1);

        try {
            String cartJson = objectMapper.writeValueAsString(cart);
            Duration ttl = cart.getUserID() != null ? USER_CART_TTL : GUEST_CART_TTL;

            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            script.setScriptText(UPDATE_CART_SCRIPT);
            script.setResultType(Long.class);

            Long result = redisTemplate.execute(
                    script,
                    Collections.singletonList(key),
                    cartJson,
                    String.valueOf(cart.getVersion() - 1),
                    String.valueOf(ttl.getSeconds())
            );

            return result != null && result == 1;
        } catch (Exception e) {
            log.error("Error updating cart atomically from Redis: {}", key, e);
            return false;
        }
    }

    public boolean addItem(UUID userID, UUID sessionID, CartItemDTO itemDTO) {
        int maxRetries = 3;

        for (int i = 0; i < maxRetries; i++) {
            Optional<Cart> cartOptional = getCart(userID, sessionID);
            Cart cart = cartOptional.orElseGet(() -> createCart(userID, sessionID));
            CartItem cartItem = cartProductMapper.toCartItemEntity(itemDTO);

            // If item already exists, update quantity
            boolean itemExists = false;
            for (CartItem existingItem : cart.getItemList()) {
                if (existingItem.getProductID().equals(cartItem.getProductID())) {
                    existingItem.setQuantity(existingItem.getQuantity() + cartItem.getQuantity());
                    itemExists = true;
                    break;
                }
            }

            if (!itemExists) {
                cart.addItem(cartItem);
            }

            if (updateCartAtomic(cart)) {
                return true;
            }

            log.info("Retry {}/{} for adding item to cart", i + 1, maxRetries);
        }

        return false;
    }

    public boolean removeItem(UUID userID, UUID sessionID, String productID) {
        int maxRetries = 3;

        for (int i = 0; i < maxRetries; i++) {
            Optional<Cart> cartOptional = getCart(userID, sessionID);

            if (cartOptional.isEmpty()) {
                return false;
            }

            Cart cart = cartOptional.get();
            cart.getItemList().removeIf(item -> item.getProductID().equals(productID));

            if (updateCartAtomic(cart)) {
                return true;
            }

            log.info("Retry {}/{} for removing item from cart", i + 1, maxRetries);
        }

        return false;
    }

    public boolean updateItemQuantity(UUID userID, UUID sessionID, String productID, int quantity) {
        int maxRetries = 3;

        for (int i = 0; i < maxRetries; i++) {
            Optional<Cart> cartOptional = getCart(userID, sessionID);

            if (cartOptional.isEmpty()) {
                return false;
            }

            Cart cart = cartOptional.get();

            for (CartItem item : cart.getItemList()) {
                if (item.getProductID().equals(productID)) {
                    if (quantity <= 0) {
                        cart.getItemList().remove(item);
                    } else {
                        item.setQuantity(quantity);
                    }
                    break;
                }
            }

            if (updateCartAtomic(cart)) {
                return true;
            }

            log.info("Retry {}/{} for updating item quantity", i + 1, maxRetries);
        }

        return false;
    }

    public boolean clearCart(UUID userID, UUID sessionID) {
        int maxRetries = 3;

        for (int i = 0; i < maxRetries; i++) {
            Optional<Cart> cartOptional = getCart(userID, sessionID);

            if (cartOptional.isEmpty()) {
                return false;
            }

            Cart cart = cartOptional.get();
            cart.getItemList().clear();

            if (updateCartAtomic(cart)) {
                return true;
            }

            log.info("Retry {}/{} for clearing cart", i + 1, maxRetries);
        }

        return false;
    }

    private String buildKey(UUID userID, UUID sessionID) {
        if (null != userID) {
            return USER_CART_PREFIX + userID;
        }
        return GUEST_CART_PREFIX + sessionID;
    }
}
