package com.gamersblended.junes.dto.request;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecommendedProductRequestDTOTest {

    @Test
    void getHistoryCache_returnsEmptyList_whenNoItemsHaveBeenSet() {
        RecommendedProductRequestDTO dto = new RecommendedProductRequestDTO();

        assertThat(dto.getHistoryCache()).isEmpty();
    }

    @Test
    void getHistoryCache_returnsEmptyList_whenSetToNull() {
        RecommendedProductRequestDTO dto = new RecommendedProductRequestDTO();

        dto.setHistoryCache(null);

        assertThat(dto.getHistoryCache()).isEmpty();
    }

    @Test
    void getHistoryCache_returnsUnmodifiableView_thatThrowsOnMutation() {
        RecommendedProductRequestDTO.HistoryItem item = new RecommendedProductRequestDTO.HistoryItem();
        item.setProductID("prod-1");
        item.setViewAt(LocalDateTime.of(2026, Month.JANUARY, 15, 10, 30));

        RecommendedProductRequestDTO dto = new RecommendedProductRequestDTO();
        dto.setHistoryCache(List.of(item));

        List<RecommendedProductRequestDTO.HistoryItem> historyCache = dto.getHistoryCache();

        assertThat(historyCache).containsExactly(item);
        assertThatThrownBy(() -> historyCache.add(item))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
