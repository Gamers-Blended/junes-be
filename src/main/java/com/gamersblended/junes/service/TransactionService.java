package com.gamersblended.junes.service;

import com.gamersblended.junes.dto.TransactionHistoryDTO;
import com.gamersblended.junes.dto.TransactionItemDTO;
import com.gamersblended.junes.model.Product;
import com.gamersblended.junes.model.Transaction;
import com.gamersblended.junes.model.TransactionItem;
import com.gamersblended.junes.repository.jpa.TransactionItemRepository;
import com.gamersblended.junes.repository.jpa.TransactionRepository;
import com.gamersblended.junes.repository.mongodb.ProductRepository;
import com.gamersblended.junes.util.PageableValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TransactionService {

    private final PageableValidator pageableValidator;
    private final TransactionRepository transactionRepository;
    private final TransactionItemRepository transactionItemRepository;
    private final ProductRepository productRepository;

    public TransactionService(PageableValidator pageableValidator,
                              TransactionRepository transactionRepository,
                              TransactionItemRepository transactionItemRepository,
                              ProductRepository productRepository) {
        this.pageableValidator = pageableValidator;
        this.transactionRepository = transactionRepository;
        this.transactionItemRepository = transactionItemRepository;
        this.productRepository = productRepository;
    }

    public Page<TransactionHistoryDTO> getTransactionHistory(UUID userID, Pageable pageable) {
        pageable = pageableValidator.sanitizePageable(pageable);

        Page<Transaction> userTransactionHistory = transactionRepository.findByUserID(userID, pageable);

        // Each transaction has at least 1 item
        List<UUID> transactionIDList = userTransactionHistory.getContent().stream()
                .map(Transaction::getTransactionID)
                .toList();

        List<TransactionItem> items = transactionItemRepository.findByTransactionIDs(transactionIDList);

        // Map: TransactionID - List<TransactionItem>
        Map<UUID, List<TransactionItem>> itemsByTransaction = items.stream()
                .collect(Collectors.groupingBy(item -> item.getTransaction().getTransactionID()));

        Set<String> productIDList = items.stream()
                .map(TransactionItem::getProductID)
                .collect(Collectors.toSet());

        List<Product> productList = productRepository.findByIdIn(productIDList);
        // Map: ProductID - Product
        Map<String, Product> productMap = productList.stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        List<TransactionHistoryDTO> transactionHistoryDTOList = userTransactionHistory.getContent().stream()
                .map(t -> buildTransactionHistoryDTO(t, itemsByTransaction.get(t.getTransactionID()), productMap))
                .toList();

        return new PageImpl<>(transactionHistoryDTOList, pageable, userTransactionHistory.getTotalElements());
    }

    private TransactionHistoryDTO buildTransactionHistoryDTO(Transaction transaction, List<TransactionItem> transactionItemList, Map<String, Product> productMap) {
        TransactionHistoryDTO transactionHistoryDTO = new TransactionHistoryDTO();
        transactionHistoryDTO.setOrderNumber(transaction.getOrderNumber());
        transactionHistoryDTO.setOrderDate(transaction.getOrderDate());
        transactionHistoryDTO.setStatus(transaction.getStatus());
        transactionHistoryDTO.setTotalAmount(transaction.getTotalAmount());

        List<TransactionItemDTO> transactionItemDTOList = new ArrayList<>();

        for (TransactionItem currentItem : transactionItemList) {
            TransactionItemDTO itemDTO = new TransactionItemDTO();

            Product productMetadata = productMap.get(currentItem.getProductID());
            if (null != productMetadata) {
                itemDTO.setName(productMetadata.getName());
                itemDTO.setSlug(productMetadata.getSlug());
                itemDTO.setPrice(productMetadata.getPrice());
                itemDTO.setPlatform(productMetadata.getPlatform());
                itemDTO.setRegion(productMetadata.getRegion());
                itemDTO.setEdition(productMetadata.getRegion());
                itemDTO.setProductImageUrl(productMetadata.getRegion());
            }

            itemDTO.setQuantity(currentItem.getQuantity());

            transactionItemDTOList.add(itemDTO);
        }

        transactionHistoryDTO.setTransactionItemDTOList(transactionItemDTOList);

        return transactionHistoryDTO;
    }
}
