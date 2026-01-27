package com.gamersblended.junes.service;

import com.gamersblended.junes.dto.TransactionDetailsDTO;
import com.gamersblended.junes.dto.TransactionHistoryDTO;
import com.gamersblended.junes.dto.TransactionItemDTO;
import com.gamersblended.junes.exception.SavedItemNotFoundException;
import com.gamersblended.junes.exception.TransactionNotFoundException;
import com.gamersblended.junes.mapper.AddressMapper;
import com.gamersblended.junes.model.Address;
import com.gamersblended.junes.model.Product;
import com.gamersblended.junes.model.Transaction;
import com.gamersblended.junes.model.TransactionItem;
import com.gamersblended.junes.repository.jpa.AddressRepository;
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
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TransactionService {

    private final PageableValidator pageableValidator;
    private final AddressMapper addressMapper;
    private final TransactionRepository transactionRepository;
    private final TransactionItemRepository transactionItemRepository;
    private final ProductRepository productRepository;
    private final AddressRepository addressRepository;

    public TransactionService(PageableValidator pageableValidator,
                              AddressMapper addressMapper,
                              TransactionRepository transactionRepository,
                              TransactionItemRepository transactionItemRepository,
                              ProductRepository productRepository,
                              AddressRepository addressRepository) {
        this.pageableValidator = pageableValidator;
        this.addressMapper = addressMapper;
        this.transactionRepository = transactionRepository;
        this.transactionItemRepository = transactionItemRepository;
        this.productRepository = productRepository;
        this.addressRepository = addressRepository;
    }

    public Page<TransactionHistoryDTO> getTransactionHistory(UUID userID, Pageable pageable) {
        pageable = pageableValidator.sanitizePageable(pageable);

        Page<Transaction> userTransactionHistory = transactionRepository.findByUserID(userID, pageable);
        log.info("Number of transactions retrieved for page: {}, {}", userTransactionHistory.getNumberOfElements(), pageable.getPageNumber());

        // Each transaction has at least 1 item
        List<UUID> transactionIDList = userTransactionHistory.getContent().stream()
                .map(Transaction::getTransactionID)
                .toList();

        List<TransactionItem> items = transactionItemRepository.findByTransactionIDs(transactionIDList);

        Map<UUID, List<TransactionItem>> itemsByTransaction = getItemsByTransactionIDMap(items);

        Map<String, Product> productMap = getProductsByIDMap(items, TransactionItem::getProductID);

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

        List<TransactionItemDTO> transactionItemDTOList = getTransactionItemDTOList(transactionItemList, productMap);

        transactionHistoryDTO.setTransactionItemDTOList(transactionItemDTOList);

        return transactionHistoryDTO;
    }

    public TransactionDetailsDTO getTransactionDetails(UUID userID, UUID transactionID) {
        Transaction transaction = transactionRepository.findByUserIDAndTransactionID(userID, transactionID)
                .orElseThrow(() -> {
                    log.error("Transaction with ID: {} not found for user: {}", transactionID, userID);
                    return new TransactionNotFoundException("Transaction not found");
                });

        List<TransactionItem> items = transactionItemRepository.findByTransactionID(transactionID);

        Map<UUID, List<TransactionItem>> itemsByTransaction = getItemsByTransactionIDMap(items);

        Map<String, Product> productMap = getProductsByIDMap(items, TransactionItem::getProductID);

        return buildTransactionDetailsDTO(userID, transaction, itemsByTransaction.get(transactionID), productMap);
    }

    private TransactionDetailsDTO buildTransactionDetailsDTO(UUID userID, Transaction transaction, List<TransactionItem> transactionItemList, Map<String, Product> productMap) {
        TransactionDetailsDTO transactionDetailsDTO = new TransactionDetailsDTO();
        transactionDetailsDTO.setOrderNumber(transaction.getOrderNumber());
        transactionDetailsDTO.setOrderDate(transaction.getOrderDate());
        transactionDetailsDTO.setShippedDate(transaction.getShippedDate());
        transactionDetailsDTO.setTotalAmount(transaction.getTotalAmount());
        transactionDetailsDTO.setShippingCost(transaction.getShippingCost());
        transactionDetailsDTO.setShippingWeight(transaction.getShippingWeight());
        transactionDetailsDTO.setTrackingNumber(transaction.getTrackingNumber());

        Address address = addressRepository.getAddressByUserIDAndID(userID, transaction.getShippingAddressID())
                .orElseThrow(() -> {
                    log.error("Address with ID: {} not found for user: {}", transaction.getShippingAddressID(), userID);
                    return new SavedItemNotFoundException("Address not found");
                });

        transactionDetailsDTO.setShippingAddress(addressMapper.toDTO(address));

        List<TransactionItemDTO> transactionItemDTOList = getTransactionItemDTOList(transactionItemList, productMap);
        log.info("Number of items for transactionID: {}, {}", transaction.getTransactionID(), transactionItemDTOList.size());

        transactionDetailsDTO.setTransactionItemDTOList(transactionItemDTOList);

        return transactionDetailsDTO;
    }

    // List<TransactionItem> -> List<TransactionItemDTO>
    private List<TransactionItemDTO> getTransactionItemDTOList(List<TransactionItem> transactionItemList, Map<String, Product> productMap) {
        List<TransactionItemDTO> transactionItemDTOList = new ArrayList<>();

        for (TransactionItem currentItem : transactionItemList) {
            TransactionItemDTO itemDTO = new TransactionItemDTO();

            // Add product metadata into current item
            Product productMetadata = productMap.get(currentItem.getProductID());
            if (null != productMetadata) {
                itemDTO.setName(productMetadata.getName());
                itemDTO.setSlug(productMetadata.getSlug());
                itemDTO.setPrice(productMetadata.getPrice());
                itemDTO.setPlatform(productMetadata.getPlatform());
                itemDTO.setRegion(productMetadata.getRegion());
                itemDTO.setEdition(productMetadata.getEdition());
                itemDTO.setProductImageUrl(productMetadata.getProductImageUrl());
            }

            itemDTO.setQuantity(currentItem.getQuantity());

            transactionItemDTOList.add(itemDTO);
        }

        return transactionItemDTOList;
    }

    // Map: TransactionID - List<TransactionItem>
    private Map<UUID, List<TransactionItem>> getItemsByTransactionIDMap(List<TransactionItem> transactionItemList) {
        return transactionItemList.stream()
                .collect(Collectors.groupingBy(item -> item.getTransaction().getTransactionID()));
    }

    // Map: ProductID - Product
    // Functional interface - takes in both Entity and DTO
    public <T> Map<String, Product> getProductsByIDMap(List<T> itemList, Function<T, String> productIDExtractor) {
        // Get all product IDs from transaction item DTOs
        Set<String> productIDList = itemList.stream()
                .map(productIDExtractor)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<Product> productList = productRepository.findByIdIn(productIDList);
        return productList.stream()
                .collect(Collectors.toMap(Product::getId, p -> p));
    }
}
