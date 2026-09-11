package com.gamersblended.junes.dto;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecommendedProductNotLoggedRequestDTOTest {

    @Test
    void setHistoryCache_copiesSetContentsIntoList() {
        RecommendedProductNotLoggedRequestDTO dto = new RecommendedProductNotLoggedRequestDTO();

        dto.setHistoryCache(Set.of("prod-1"));

        assertThat(dto.getHistoryCache()).containsExactly("prod-1");
    }

    @Test
    void getHistoryCache_returnsUnmodifiableView_thatThrowsOnMutation() {
        RecommendedProductNotLoggedRequestDTO dto = new RecommendedProductNotLoggedRequestDTO();
        dto.setHistoryCache(Set.of("prod-1"));

        List<String> historyCache = dto.getHistoryCache();

        assertThatThrownBy(() -> historyCache.add("prod-2"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
