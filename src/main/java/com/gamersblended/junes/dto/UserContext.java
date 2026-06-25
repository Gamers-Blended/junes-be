package com.gamersblended.junes.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class UserContext {
    private final UUID userID;
    private final String sessionID;
    private final List<RecommendationInputDTO> productIDList;
    private final String pageType;

}