package com.gamersblended.junes.service;

import com.gamersblended.junes.constant.SignalEnums;
import com.gamersblended.junes.dto.OrderEvent;
import com.gamersblended.junes.dto.RecommendationInputDTO;
import com.gamersblended.junes.dto.UserContext;
import com.gamersblended.junes.dto.request.RecommendedProductRequestDTO;
import com.gamersblended.junes.repository.jpa.TransactionRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductRecommendationContextBuilder {

    private static final int MAX_PURCHASED_ITEMS_SIZE = 30;

    private final TransactionRepository transactionRepository;

    public UserContext buildUserContext(RecommendedProductRequestDTO requestDTO, HttpServletRequest httpRequest) {
        List<RecommendationInputDTO> productIDList = new ArrayList<>(requestDTO.getHistoryCache().stream()
                .map(item -> new RecommendationInputDTO(item.getProductID(),
                        item.getViewAt(),
                        SignalEnums.BROWSE))
                .toList());

        List<RecommendationInputDTO> purchasedProductIDList = requestDTO.getUserID() != null
                ? fetchOrderHistory(requestDTO.getUserID())
                : Collections.emptyList();

        if (!purchasedProductIDList.isEmpty()) {
            productIDList.addAll(purchasedProductIDList);
        }

        return UserContext.builder()
                .userID(requestDTO.getUserID())
                .sessionID(httpRequest.getSession().getId())
                .productIDList(productIDList)
                .pageType(httpRequest.getHeader("X-Page-Type"))
                .build();
    }

    private List<RecommendationInputDTO> fetchOrderHistory(UUID userID) {
        List<OrderEvent> productIDList = transactionRepository.findRecentItemsByUserID(userID, PageRequest.of(0, MAX_PURCHASED_ITEMS_SIZE));

        return productIDList.stream()
                .map(item -> new RecommendationInputDTO(item.getProductID(),
                        item.getCreatedOn(),
                        SignalEnums.PURCHASE))
                .toList();
    }
}
