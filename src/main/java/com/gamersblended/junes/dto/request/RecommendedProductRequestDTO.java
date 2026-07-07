package com.gamersblended.junes.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
@Setter
public class RecommendedProductRequestDTO {

    @Valid
    private List<HistoryItem> historyCache = new ArrayList<>();

    public List<HistoryItem> getHistoryCache() {
        return historyCache == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(historyCache);
    }

    @Getter
    @Setter
    public static class HistoryItem {
        @NotBlank(message = "Product ID cannot be blank")
        private String productID;

        @NotNull(message = "View date cannot be null")
        private LocalDateTime viewAt;
    }
}
