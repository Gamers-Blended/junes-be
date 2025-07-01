package com.gamersblended.junes.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.*;

@Getter
@Setter
public class RecommendedProductNotLoggedRequestDTO {

    private List<String> historyCache;

    public List<String> getHistoryCache() {
        return Collections.unmodifiableList(historyCache);
    }

    public void setHistoryCache(Set<String> historyCache) {
        this.historyCache = new ArrayList<>(historyCache);
    }
}
