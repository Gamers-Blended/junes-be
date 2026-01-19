package com.gamersblended.junes.service;

import com.gamersblended.junes.dto.TransactionItemDTO;
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

    public String getShippingFee(List<TransactionItemDTO> transactionItemDTOList) {
        DecimalFormat df = new DecimalFormat("$#,##0.00");

        if (null == transactionItemDTOList || transactionItemDTOList.isEmpty()) {
            return df.format(0.00);
        }

        BigDecimal totalShippingWeight = getTotalShippingWeight(transactionItemDTOList);
        log.info("Total shipping weight is {}", totalShippingWeight);

        if (totalShippingWeight.compareTo(BigDecimal.ZERO) < 0) {
            List<String> productIDList = transactionItemDTOList.stream()
                    .map(TransactionItemDTO::getProductID)
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

    public BigDecimal getTotalShippingWeight(List<TransactionItemDTO> transactionItemDTOList) {
        Set<String> expectedProductIDSet = transactionItemDTOList.stream()
                .map(TransactionItemDTO::getProductID)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<String, Product> productMap = transactionService.getProductsByIDMap(transactionItemDTOList, TransactionItemDTO::getProductID);

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

        return transactionItemDTOList.stream()
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
