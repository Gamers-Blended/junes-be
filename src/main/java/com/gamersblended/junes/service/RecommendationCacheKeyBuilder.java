package com.gamersblended.junes.service;

import com.gamersblended.junes.dto.recommender.ProductSignalDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RecommendationCacheKeyBuilder {

    private static final String KEY_PREFIX = "recommendations:";

    public String buildKey(List<ProductSignalDTO> signalDTOList) {
        if (null == signalDTOList || signalDTOList.isEmpty()) {
            return KEY_PREFIX + "empty";
        }

        String sortedIDs = signalDTOList.stream()
                .map(ProductSignalDTO::getProductID)
                .filter(Objects::nonNull)
                .sorted() // order-independent
                .collect(Collectors.joining(","));

        // MD5 for fixed-length, collision-resistant strings
        String hash = DigestUtils.md5DigestAsHex(sortedIDs.getBytes(StandardCharsets.UTF_8));

        log.info("[CacheKeyBuilder] signals = {} -> key = {}{}", sortedIDs, KEY_PREFIX, hash);
        return KEY_PREFIX + hash;
    }
}
