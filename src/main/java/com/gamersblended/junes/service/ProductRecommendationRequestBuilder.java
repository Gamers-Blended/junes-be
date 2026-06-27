package com.gamersblended.junes.service;

import com.gamersblended.junes.constant.SignalEnums;
import com.gamersblended.junes.dto.OrderEvent;
import com.gamersblended.junes.dto.RecommendationInputDTO;
import com.gamersblended.junes.dto.UserContext;
import com.gamersblended.junes.dto.request.RecommendedProductRequestDTO;
import com.gamersblended.junes.model.Cart;
import com.gamersblended.junes.model.CartItem;
import com.gamersblended.junes.repository.jpa.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductRecommendationRequestBuilder {

    private static final int MAX_ITEMS_SIZE = 30;

    private final TransactionRepository transactionRepository;
    private final CartService cartService;

    public UserContext buildUserContext(RecommendedProductRequestDTO requestDTO, UUID sessionID) {
        // (1) Browsing cache
        List<RecommendationInputDTO> productIDList = new ArrayList<>(requestDTO.getHistoryCache().stream()
                .map(item -> new RecommendationInputDTO(item.getProductID(),
                        item.getViewAt(),
                        SignalEnums.BROWSE))
                .toList());

        // (2) Purchased items
        List<RecommendationInputDTO> purchasedProductIDList = requestDTO.getUserID() != null
                ? fetchOrderHistory(requestDTO.getUserID())
                : Collections.emptyList();

        if (!purchasedProductIDList.isEmpty()) {
            productIDList.addAll(purchasedProductIDList);
        }

        // (3) Cart items
        Cart cart = cartService.getOrCreateCart(requestDTO.getUserID(), sessionID);
        List<CartItem> cartItemList = cart.getItemList();

        // Keep only the most recent n products in cartItemList
        if (null != cartItemList && cartItemList.size() > MAX_ITEMS_SIZE) {
            log.info("cartItemList exceeded max capacity, keeping only the most recent {} products...", MAX_ITEMS_SIZE);

            List<CartItem> sortedCartItemList = new ArrayList<>(cartItemList);
            sortedCartItemList.sort(Comparator.comparing(CartItem::getCreatedOn).reversed());

            cartItemList = sortedCartItemList.stream()
                    .limit(MAX_ITEMS_SIZE)
                    .toList();
        }

        if (null != cartItemList && !cartItemList.isEmpty()) {
            List<RecommendationInputDTO> cartProductIDList = cartItemList.stream()
                    .map(item -> new RecommendationInputDTO(item.getProductID(),
                            item.getCreatedOn(),
                            SignalEnums.CART_ADD))
                    .toList();

            productIDList.addAll(cartProductIDList);
        }

        return UserContext.builder()
                .userID(requestDTO.getUserID())
                .sessionID(sessionID)
                .productIDList(productIDList)
                .build();
    }

    private List<RecommendationInputDTO> fetchOrderHistory(UUID userID) {
        List<OrderEvent> productIDList = transactionRepository.findRecentItemsByUserID(userID, PageRequest.of(0, MAX_ITEMS_SIZE));

        return productIDList.stream()
                .map(item -> new RecommendationInputDTO(item.getProductID(),
                        item.getCreatedOn(),
                        SignalEnums.PURCHASE))
                .toList();
    }
}
