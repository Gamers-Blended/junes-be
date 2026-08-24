package com.gamersblended.junes.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamersblended.junes.dto.WishlistItemDTO;
import com.gamersblended.junes.exception.RedisDataException;
import com.gamersblended.junes.exception.WishlistSerialisationException;
import com.gamersblended.junes.exception.WishlistUpdateConflictException;
import com.gamersblended.junes.mapper.WishlistProductMapper;
import com.gamersblended.junes.model.Wishlist;
import com.gamersblended.junes.model.WishlistItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Repository
public class RedisWishlistRepository {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final WishlistProductMapper wishlistProductMapper;

    public RedisWishlistRepository(RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper, WishlistProductMapper wishlistProductMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.wishlistProductMapper = wishlistProductMapper;
    }

    private static final String USER_WISHLIST_PREFIX = "user:wishlist:";
    private static final String GUEST_WISHLIST_PREFIX = "wishlist:";
    private static final Duration USER_WISHLIST_TTL = Duration.ofDays(30);
    private static final Duration GUEST_WISHLIST_TTL = Duration.ofDays(7);

    // Lua script for atomic wishlist update with optimistic locking
    private static final String UPDATE_WISHLIST_SCRIPT =
            """
                    local key = KEYS[1]
                    local newWishlistJson = ARGV[1]
                    local expectedVersion = tonumber(ARGV[2])
                    local ttl = tonumber(ARGV[3])
                    
                    local currentWishlistJson = redis.call('GET', key)
                    
                    if currentWishlistJson == false then
                      -- Wishlist doesn't exist, create new
                      redis.call('SET', key, newWishlistJson, 'EX', ttl)
                      return 1
                    end
                    
                    local currentWishlist = cjson.decode(currentWishlistJson)
                    
                    if currentWishlist.version ~= expectedVersion then
                      -- Version mismatch, concurrent modification
                      return 0
                    end
                    
                    -- Update wishlist
                    redis.call('SET', key, newWishlistJson, 'EX', ttl)
                    return 1
                    """;

    public Wishlist createWishlist(UUID userID, UUID sessionID) {
        Wishlist wishlist = Wishlist.builder()
                .wishlistID(UUID.randomUUID())
                .userID(userID)
                .sessionID(sessionID)
                .createdOn(LocalDateTime.now(ZoneId.of("Asia/Singapore")))
                .version(0)
                .build();

        saveWishlist(wishlist);
        return wishlist;
    }

    public Optional<Wishlist> getWishlist(UUID userID, UUID sessionID) {
        String key = buildKey(userID, sessionID);
        String wishlistJson = redisTemplate.opsForValue().get(key);

        if (null == wishlistJson) {
            return Optional.empty();
        }

        try {
            Wishlist wishlist = objectMapper.readValue(wishlistJson, Wishlist.class);
            return Optional.of(wishlist);
        } catch (Exception ex) {
            log.error("Corrupt wishlist data at key = {}", key, ex);
            throw new RedisDataException("Failed to parse wishlist for user");
        }
    }

    public boolean deleteWishlist(UUID userID, UUID sessionID) {
        String key = buildKey(userID, sessionID);
        return Boolean.TRUE.equals(redisTemplate.delete(key));
    }

    public void saveWishlist(Wishlist wishlist) {
        String key = buildKey(wishlist.getUserID(), wishlist.getSessionID());
        wishlist.setUpdatedOn(LocalDateTime.now(ZoneId.of("Asia/Singapore")));

        try {
            String wishlistJson = objectMapper.writeValueAsString(wishlist);
            Duration ttl = wishlist.getUserID() != null ? USER_WISHLIST_TTL : GUEST_WISHLIST_TTL;

            redisTemplate.opsForValue().set(key, wishlistJson, ttl);
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialise wishlist for key: {}", key, ex);
            throw new WishlistSerialisationException("Failed to serialise wishlist");
        }
    }

    public boolean updateWishlistAtomic(Wishlist wishlist) {
        String key = buildKey(wishlist.getUserID(), wishlist.getSessionID());
        int oldVersion = wishlist.getVersion();

        // Increment version locally for update attempt
        wishlist.setUpdatedOn(LocalDateTime.now(ZoneId.of("Asia/Singapore")));
        wishlist.setVersion(oldVersion + 1);

        try {
            String wishlistJson = objectMapper.writeValueAsString(wishlist);
            Duration ttl = wishlist.getUserID() != null ? USER_WISHLIST_TTL : GUEST_WISHLIST_TTL;

            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            script.setScriptText(UPDATE_WISHLIST_SCRIPT);
            script.setResultType(Long.class);

            Long result = redisTemplate.execute(
                    script,
                    Collections.singletonList(key),
                    wishlistJson,
                    String.valueOf(oldVersion), // Check against version fetched
                    String.valueOf(ttl.getSeconds())
            );

            // 1 = Success, 0 = Version Mismatch
            if (result == 0) {
                // Revert version if update failed
                wishlist.setVersion(oldVersion);
                return false;
            }
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialise wishlist, key = {}", key, ex);
            throw new WishlistSerialisationException("Failed to serialise wishlist");
        }
        return true;
    }

    public boolean addItem(UUID userID, UUID sessionID, WishlistItemDTO itemDTO) {
        int maxRetries = 3;
        WishlistItem wishlistItem = wishlistProductMapper.toWishlistItemEntity(itemDTO);

        for (int i = 0; i < maxRetries; i++) {
            Optional<Wishlist> wishlistOptional = getWishlist(userID, sessionID);
            Wishlist wishlist = wishlistOptional.orElseGet(() -> createWishlist(userID, sessionID));

            addItemIfAbsent(wishlist, wishlistItem);

            if (updateWishlistAtomic(wishlist)) {
                return true;
            }

            log.info("Retry {}/{} for adding item to wishlist", i + 1, maxRetries);
        }

        throw new WishlistUpdateConflictException("Failed to add item to wishlist after " + maxRetries + " retries due to concurrent modification");
    }

    public boolean removeItem(UUID userID, UUID sessionID, String productID) {
        int maxRetries = 3;

        for (int i = 0; i < maxRetries; i++) {
            Optional<Wishlist> wishlistOptional = getWishlist(userID, sessionID);

            if (wishlistOptional.isEmpty()) {
                return false;
            }

            Wishlist wishlist = wishlistOptional.get();
            wishlist.getItemList().removeIf(item -> item.getProductID().equals(productID));

            if (updateWishlistAtomic(wishlist)) {
                return true;
            }

            log.info("Retry {}/{} for removing item from wishlist", i + 1, maxRetries);
        }

        throw new WishlistUpdateConflictException("Failed to remove item from wishlist after " + maxRetries + " retries due to concurrent modification");
    }

    // Unlike cart's addItem (which increments quantity on duplicate), a wishlist item either exists or doesn't
    public void addItemIfAbsent(Wishlist wishlist, WishlistItem newItem) {
        boolean alreadyPresent = wishlist.getItemList().stream()
                .anyMatch(item -> item.getProductID().equals(newItem.getProductID()));

        if (!alreadyPresent) {
            wishlist.addItem(newItem);
        }
    }

    public boolean clearWishlist(UUID userID, UUID sessionID) {
        int maxRetries = 3;

        for (int i = 0; i < maxRetries; i++) {
            Optional<Wishlist> wishlistOptional = getWishlist(userID, sessionID);

            if (wishlistOptional.isEmpty()) {
                return false;
            }

            Wishlist wishlist = wishlistOptional.get();
            wishlist.getItemList().clear();

            if (updateWishlistAtomic(wishlist)) {
                return true;
            }

            log.info("Retry {}/{} for clearing wishlist", i + 1, maxRetries);
        }

        throw new WishlistUpdateConflictException("Failed to clear wishlist after " + maxRetries + " retries due to concurrent modification");
    }

    private String buildKey(UUID userID, UUID sessionID) {
        if (null != userID) {
            return USER_WISHLIST_PREFIX + userID;
        }
        return GUEST_WISHLIST_PREFIX + sessionID;
    }
}
