package com.gamersblended.junes.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class RecommendedProductNotLoggedRequestDTO {

    private Integer pageNumber;
    private List<String> historyCache;
}
