package com.gamersblended.junes.service;

import com.gamersblended.junes.dto.OrderItemDTO;
import com.gamersblended.junes.exception.InvalidProductIdException;
import com.gamersblended.junes.exception.NegativeWeightException;
import com.gamersblended.junes.model.Product;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ShippingService {

    private final TransactionService transactionService;

    public ShippingService(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    public String getShippingFee(List<OrderItemDTO> orderItemDTOList) {
        DecimalFormat df = new DecimalFormat("$#,##0.00");

        if (null == orderItemDTOList || orderItemDTOList.isEmpty()) {
            return df.format(0.00);
        }

        BigDecimal totalShippingWeight = getTotalShippingWeight(orderItemDTOList);
        log.info("Total shipping weight is {}", totalShippingWeight);

        if (totalShippingWeight.compareTo(BigDecimal.ZERO) < 0) {
            List<String> productIDList = orderItemDTOList.stream()
                    .map(OrderItemDTO::getProductID)
                    .toList();
            log.error("Total shipping weight of these products is negative: {}", productIDList);
            throw new NegativeWeightException("Total shipping weight is negative. Check product weights and item quantities");
        }


        if (totalShippingWeight.compareTo(BigDecimal.ZERO) <= 0) {
            return df.format(0.00);
        } else if (totalShippingWeight.compareTo(BigDecimal.ONE) <= 0) {
            return df.format(5.00);
        } else if (totalShippingWeight.compareTo(BigDecimal.valueOf(5)) <= 0) {
            return df.format(7.00);
        } else if (totalShippingWeight.compareTo(BigDecimal.valueOf(10)) <= 0) {
            return df.format(10.00);
        } else {
            return df.format(15.00);
        }
    }

    public BigDecimal getTotalShippingWeight(List<OrderItemDTO> orderItemDTOList) {
        Map<String, Product> productMap = transactionService.getProductsByIDMap(orderItemDTOList, OrderItemDTO::getProductID);

        return getTotalShippingWeight(orderItemDTOList, productMap);
    }

    public BigDecimal getTotalShippingWeight(List<OrderItemDTO> orderItemDTOList, Map<String, Product> productMap) {
        Set<String> expectedProductIDSet = orderItemDTOList.stream()
                .map(OrderItemDTO::getProductID)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Check that all product metadata is retrieved
        List<String> missingProductIDList = expectedProductIDSet.stream()
                .filter(id -> {
                    Product p = productMap.get(id);
                    return null == p || null == p.getWeight() || p.getWeight().compareTo(BigDecimal.ZERO) <= 0;
                })
                .toList();

        if (!missingProductIDList.isEmpty()) {
            log.error("Missing or invalid product(s): {}", missingProductIDList);
            throw new InvalidProductIdException("Missing or invalid product data for ID(s): " + missingProductIDList);
        }

        return orderItemDTOList.stream()
                .filter(item -> null != item.getProductID())
                .map(item -> {
                    Product product = productMap.get(item.getProductID());
                    BigDecimal weight = product.getWeight();
                    BigDecimal quantity = BigDecimal.valueOf(item.getQuantity());
                    return weight.multiply(quantity);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
