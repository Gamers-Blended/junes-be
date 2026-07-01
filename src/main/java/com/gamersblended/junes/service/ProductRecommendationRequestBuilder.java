package com.gamersblended.junes.service;

import com.gamersblended.junes.constant.SignalTypeEnums;
import com.gamersblended.junes.dto.recommender.OrderEvent;
import com.gamersblended.junes.dto.recommender.ProductSignalDTO;
import com.gamersblended.junes.dto.recommender.RecommendationRequestDTO;
import com.gamersblended.junes.dto.request.RecommendedProductRequestDTO;
import com.gamersblended.junes.model.Cart;
import com.gamersblended.junes.model.CartItem;
import com.gamersblended.junes.repository.jpa.TransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ProductRecommendationRequestBuilder {

    private static final int MAX_ITEMS_SIZE = 30;
    private static final int MAX_RESULTS = 20;
    private static final String ADD_ID_TO_LIST_LOG_MESSAGE = "Adding {} IDs under {} to request body for recommender system";

    private final TransactionRepository transactionRepository;
    private final CartService cartService;

    @Autowired
    public ProductRecommendationRequestBuilder(TransactionRepository transactionRepository, CartService cartService) {
        this.transactionRepository = transactionRepository;
        this.cartService = cartService;
    }

    public List<ProductSignalDTO> getRecommendationInputDTOList(RecommendedProductRequestDTO requestDTO, UUID sessionID) {
        // (1) Browsing cache
        log.info(ADD_ID_TO_LIST_LOG_MESSAGE, requestDTO.getHistoryCache().size(), SignalTypeEnums.BROWSE);
        List<ProductSignalDTO> productIDList = new ArrayList<>(requestDTO.getHistoryCache().stream()
                .map(item -> new ProductSignalDTO(item.getProductID(),
                        SignalTypeEnums.BROWSE.getName(),
                        item.getViewAt()
                ))
                .toList());

        // (2) Purchased items
        List<ProductSignalDTO> purchasedProductIDList = requestDTO.getUserID() != null
                ? fetchOrderHistory(requestDTO.getUserID())
                : Collections.emptyList();

        if (!purchasedProductIDList.isEmpty()) {
            log.info(ADD_ID_TO_LIST_LOG_MESSAGE, purchasedProductIDList.size(), SignalTypeEnums.PURCHASE);
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
            log.info(ADD_ID_TO_LIST_LOG_MESSAGE, cartItemList.size(), SignalTypeEnums.CART_ADD);
            List<ProductSignalDTO> cartProductIDList = cartItemList.stream()
                    .map(item -> new ProductSignalDTO(item.getProductID(),
                            SignalTypeEnums.CART_ADD.getName(),
                            item.getCreatedOn()))
                    .toList();

            productIDList.addAll(cartProductIDList);
        }

        return filterHighestWeightSignals(productIDList);
    }

    public RecommendationRequestDTO getRecommendationRequestDTO(List<ProductSignalDTO> productSignalDTOList) {
        RecommendationRequestDTO recommendationRequestDTO = new RecommendationRequestDTO();
        recommendationRequestDTO.setSignalList(productSignalDTOList);
        recommendationRequestDTO.setMaxResult(MAX_RESULTS);

        return recommendationRequestDTO;
    }

    private List<ProductSignalDTO> fetchOrderHistory(UUID userID) {
        List<OrderEvent> productIDList = transactionRepository.findRecentItemsByUserID(userID, PageRequest.of(0, MAX_ITEMS_SIZE))
                .stream()
                .map(row -> new OrderEvent(
                        (String) row[0], // productID
                        ((Timestamp) row[1]).toLocalDateTime() // createdOn
                ))
                .toList();

        return productIDList.stream()
                .map(item -> new ProductSignalDTO(item.getProductID(),
                        SignalTypeEnums.PURCHASE.getName(),
                        item.getCreatedOn()))
                .toList();
    }

    private List<ProductSignalDTO> filterHighestWeightSignals(List<ProductSignalDTO> productSignalDTOList) {
        Map<String, ProductSignalDTO> highestWeightMap = productSignalDTOList.stream()
                .collect(Collectors.toMap(
                        ProductSignalDTO::getProductID,
                        signal -> signal,
                        (existing, replacement) -> {
                            int existingWeight = SignalTypeEnums.valueOf(existing.getType()).getWeight();
                            int replacementWeight = SignalTypeEnums.valueOf(replacement.getType()).getWeight();

                            return existingWeight >= replacementWeight ? existing : replacement;
                        }
                ));

        return new ArrayList<>(highestWeightMap.values());
    }
}
