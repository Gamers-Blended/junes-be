package com.gamersblended.junes.service.order;

import com.gamersblended.junes.dto.TransactionDetailsDTO;
import com.gamersblended.junes.dto.TransactionHistoryDTO;
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
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private PageableValidator pageableValidator;
    @Mock
    private AddressMapper addressMapper;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private TransactionItemRepository transactionItemRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private AddressRepository addressRepository;

    private TransactionService transactionService;

    @BeforeEach
    void setUp() {
        transactionService = new TransactionService(pageableValidator, addressMapper, transactionRepository,
                transactionItemRepository, productRepository, addressRepository);
    }

    private static Transaction transaction(UUID transactionID) {
        Transaction transaction = new Transaction();
        transaction.setTransactionID(transactionID);
        transaction.setOrderNumber("ORD-1");
        transaction.setStatus("Shipped");
        transaction.setTotalAmount(BigDecimal.TEN);
        transaction.setShippingAddressID(UUID.randomUUID());
        transaction.setUserID(UUID.randomUUID());
        return transaction;
    }

    private static TransactionItem transactionItem(Transaction transaction, int quantity) {
        TransactionItem item = new TransactionItem();
        item.setTransaction(transaction);
        item.setProductID("507f1f77bcf86cd799439011");
        item.setQuantity(quantity);
        return item;
    }

    private static Product product(BigDecimal price) {
        Product product = new Product("Game", "slug", "description", price, "PS5", "US", "Standard",
                "publisher", LocalDate.now(), Set.of(), Set.of(), Set.of(), Set.of(), BigDecimal.ONE, 0, 10,
                "image.png", List.of(), LocalDate.now());
        product.setId(new ObjectId("507f1f77bcf86cd799439011"));
        return product;
    }

    // ---- getTransactionHistory ----

    @Test
    void getTransactionHistory_sanitizesPageable_beforeQuerying() {
        UUID userID = UUID.randomUUID();
        Pageable rawPageable = PageRequest.of(0, 999);
        Pageable sanitized = PageRequest.of(0, 20);
        when(pageableValidator.sanitizePageable(rawPageable)).thenReturn(sanitized);
        when(transactionRepository.findByUserID(userID, sanitized)).thenReturn(Page.empty(sanitized));
        when(transactionItemRepository.findByTransactionIDs(anyList())).thenReturn(List.of());

        transactionService.getTransactionHistory(userID, rawPageable);

        verify(transactionRepository).findByUserID(userID, sanitized);
    }

    @Test
    void getTransactionHistory_returnsEmptyPage_whenNoTransactions() {
        UUID userID = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);
        when(pageableValidator.sanitizePageable(pageable)).thenReturn(pageable);
        when(transactionRepository.findByUserID(userID, pageable)).thenReturn(Page.empty(pageable));
        when(transactionItemRepository.findByTransactionIDs(anyList())).thenReturn(List.of());

        Page<TransactionHistoryDTO> result = transactionService.getTransactionHistory(userID, pageable);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void getTransactionHistory_buildsDTOListWithItemsAndProductMetadata() {
        UUID userID = UUID.randomUUID();
        UUID transactionID = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);
        Transaction transaction = transaction(transactionID);
        TransactionItem item = transactionItem(transaction, 2);
        when(pageableValidator.sanitizePageable(pageable)).thenReturn(pageable);
        when(transactionRepository.findByUserID(userID, pageable))
                .thenReturn(new PageImpl<>(List.of(transaction), pageable, 1));
        when(transactionItemRepository.findByTransactionIDs(List.of(transactionID))).thenReturn(List.of(item));
        Product product = product(BigDecimal.valueOf(50));
        when(productRepository.findByIdIn(anyList())).thenReturn(List.of(product));

        Page<TransactionHistoryDTO> result = transactionService.getTransactionHistory(userID, pageable);

        assertThat(result.getContent()).hasSize(1);
        TransactionHistoryDTO dto = result.getContent().get(0);
        assertThat(dto.getOrderNumber()).isEqualTo("ORD-1");
        assertThat(dto.getTransactionItemDTOList()).hasSize(1);
        assertThat(dto.getTransactionItemDTOList().get(0).getName()).isEqualTo("Game");
        assertThat(dto.getTransactionItemDTOList().get(0).getQuantity()).isEqualTo(2);
    }

    // ---- getTransactionDetails ----

    @Test
    void getTransactionDetails_throwsTransactionNotFoundException_whenTransactionMissing() {
        UUID userID = UUID.randomUUID();
        when(transactionRepository.findByUserIDAndOrderNumber(userID, "ORD-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.getTransactionDetails(userID, "ORD-1"))
                .isInstanceOf(TransactionNotFoundException.class);
    }

    @Test
    void getTransactionDetails_throwsSavedItemNotFoundException_whenShippingAddressMissing() {
        UUID userID = UUID.randomUUID();
        UUID transactionID = UUID.randomUUID();
        Transaction transaction = transaction(transactionID);
        when(transactionRepository.findByUserIDAndOrderNumber(userID, "ORD-1")).thenReturn(Optional.of(transaction));
        when(transactionItemRepository.findByTransactionID(transactionID)).thenReturn(List.of());
        when(addressRepository.getAddressByUserIDAndIDWithDeleted(eq(userID), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.getTransactionDetails(userID, "ORD-1"))
                .isInstanceOf(SavedItemNotFoundException.class);
    }

    @Test
    void getTransactionDetails_buildsDTOWithShippingAddressAndItems() {
        UUID userID = UUID.randomUUID();
        UUID transactionID = UUID.randomUUID();
        Transaction transaction = transaction(transactionID);
        transaction.setTrackingNumber("TRK123");
        TransactionItem item = transactionItem(transaction, 1);
        when(transactionRepository.findByUserIDAndOrderNumber(userID, "ORD-1")).thenReturn(Optional.of(transaction));
        when(transactionItemRepository.findByTransactionID(transactionID)).thenReturn(List.of(item));
        Product product = product(BigDecimal.valueOf(20));
        when(productRepository.findByIdIn(anyList())).thenReturn(List.of(product));
        Address address = new Address();
        when(addressRepository.getAddressByUserIDAndIDWithDeleted(eq(userID), any())).thenReturn(Optional.of(address));
        when(addressMapper.toDTO(address)).thenReturn(new com.gamersblended.junes.dto.AddressDTO());

        TransactionDetailsDTO dto = transactionService.getTransactionDetails(userID, "ORD-1");

        assertThat(dto.getOrderNumber()).isEqualTo("ORD-1");
        assertThat(dto.getTrackingNumber()).isEqualTo("TRK123");
        assertThat(dto.getShippingAddress()).isNotNull();
        assertThat(dto.getTransactionItemDTOList()).hasSize(1);
        assertThat(dto.getTransactionItemDTOList().get(0).getPrice()).isEqualByComparingTo(BigDecimal.valueOf(20));
    }

    // ---- getProductsByIDMap ----

    @Test
    void getProductsByIDMap_returnsEmptyMap_whenItemListEmpty() {
        Map<String, Product> result = transactionService.getProductsByIDMap(List.of(), TransactionItem::getProductID);

        assertThat(result).isEmpty();
    }

    @Test
    void getProductsByIDMap_filtersOutNullProductIDs() {
        TransactionItem item = new TransactionItem();
        item.setProductID(null);

        Map<String, Product> result = transactionService.getProductsByIDMap(List.of(item), TransactionItem::getProductID);

        assertThat(result).isEmpty();
        verify(productRepository).findByIdIn(List.of());
    }

    @Test
    void getProductsByIDMap_filtersOutInvalidObjectIdFormat() {
        TransactionItem item = new TransactionItem();
        item.setProductID("not-a-valid-object-id");

        Map<String, Product> result = transactionService.getProductsByIDMap(List.of(item), TransactionItem::getProductID);

        assertThat(result).isEmpty();
        verify(productRepository).findByIdIn(List.of());
    }

    @Test
    void getProductsByIDMap_returnsProductsKeyedByHexID() {
        TransactionItem item = new TransactionItem();
        item.setProductID("507f1f77bcf86cd799439011");
        Product product = product(BigDecimal.TEN);
        when(productRepository.findByIdIn(anyList())).thenReturn(List.of(product));

        Map<String, Product> result = transactionService.getProductsByIDMap(List.of(item), TransactionItem::getProductID);

        assertThat(result).containsKey("507f1f77bcf86cd799439011");
        assertThat(result.get("507f1f77bcf86cd799439011")).isSameAs(product);
    }
}
