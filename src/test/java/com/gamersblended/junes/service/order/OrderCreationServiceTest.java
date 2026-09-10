package com.gamersblended.junes.service.order;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamersblended.junes.constant.TransactionStatus;
import com.gamersblended.junes.dto.AddressDTO;
import com.gamersblended.junes.dto.OrderItemDTO;
import com.gamersblended.junes.dto.request.PlaceOrderRequest;
import com.gamersblended.junes.exception.OutboxEventCreationException;
import com.gamersblended.junes.exception.ProductNotFoundException;
import com.gamersblended.junes.model.OutboxEvent;
import com.gamersblended.junes.model.Product;
import com.gamersblended.junes.model.Transaction;
import com.gamersblended.junes.repository.jpa.OutboxEventRepository;
import com.gamersblended.junes.repository.jpa.TransactionRepository;
import com.gamersblended.junes.service.cart.ShippingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.gamersblended.junes.constant.KafkaConstants.ORDER_EVENTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderCreationServiceTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private ShippingService shippingService;
    @Mock
    private ObjectMapper objectMapper;

    private OrderCreationService orderCreationService;

    @BeforeEach
    void setUp() {
        orderCreationService = new OrderCreationService(transactionRepository, outboxEventRepository, shippingService, objectMapper);
    }

    private static Product product(BigDecimal price) {
        return new Product("Game", "slug", "description", price, "PS5", "US", "Standard",
                "publisher", LocalDate.now(), Set.of(), Set.of(), Set.of(), Set.of(), BigDecimal.ONE, 0, 10,
                "image.png", List.of(), LocalDate.now());
    }

    private static PlaceOrderRequest placeOrderRequest(UUID addressID, UUID paymentMethodID, BigDecimal shippingCost, OrderItemDTO... items) {
        AddressDTO addressDTO = new AddressDTO();
        addressDTO.setAddressID(addressID);
        return new PlaceOrderRequest(addressDTO, paymentMethodID, List.of(items), shippingCost);
    }

    // ---- createPendingOrder: transaction creation ----

    @Test
    void createPendingOrder_savesTransactionInPaymentPendingStatus() throws JsonProcessingException {
        UUID userID = UUID.randomUUID();
        UUID addressID = UUID.randomUUID();
        PlaceOrderRequest request = placeOrderRequest(addressID, UUID.randomUUID(), BigDecimal.valueOf(5), new OrderItemDTO(2, "p1"));
        Map<String, Integer> consolidatedItemMap = Map.of("p1", 2);
        Map<String, Product> productMap = Map.of("p1", product(BigDecimal.TEN));
        when(shippingService.getTotalShippingWeight(request.getOrderItemDTOList(), productMap)).thenReturn(BigDecimal.valueOf(2));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        Transaction result = orderCreationService.createPendingOrder(userID, request, consolidatedItemMap, productMap, "idem-key");

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.PAYMENT_PENDING.getTransactionStatusValue());
        assertThat(result.getUserID()).isEqualTo(userID);
        assertThat(result.getShippingAddressID()).isEqualTo(addressID);
        assertThat(result.getShippingCost()).isEqualByComparingTo(BigDecimal.valueOf(5));
        assertThat(result.getShippingWeight()).isEqualByComparingTo(BigDecimal.valueOf(2));
        // (10 * 2) + 5 shipping = 25
        assertThat(result.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(25));
        assertThat(result.getOrderNumber()).startsWith("J");
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getProductID()).isEqualTo("p1");
        assertThat(result.getItems().get(0).getQuantity()).isEqualTo(2);
        assertThat(result.getItems().get(0).getTransaction()).isSameAs(result);
    }

    @Test
    void createPendingOrder_sumsItemTotalsAcrossMultipleProducts() throws JsonProcessingException {
        UUID userID = UUID.randomUUID();
        PlaceOrderRequest request = placeOrderRequest(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ZERO,
                new OrderItemDTO(2, "p1"), new OrderItemDTO(3, "p2"));
        Map<String, Integer> consolidatedItemMap = Map.of("p1", 2, "p2", 3);
        Map<String, Product> productMap = Map.of("p1", product(BigDecimal.valueOf(10)), "p2", product(BigDecimal.valueOf(5)));
        when(shippingService.getTotalShippingWeight(request.getOrderItemDTOList(), productMap)).thenReturn(BigDecimal.ZERO);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        Transaction result = orderCreationService.createPendingOrder(userID, request, consolidatedItemMap, productMap, "idem-key");

        // (10 * 2) + (5 * 3) + 0 shipping = 35
        assertThat(result.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(35));
    }

    @Test
    void createPendingOrder_throwsProductNotFoundException_whenProductMissingFromMap() {
        UUID userID = UUID.randomUUID();
        PlaceOrderRequest request = placeOrderRequest(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ZERO, new OrderItemDTO(1, "missing"));
        Map<String, Integer> consolidatedItemMap = Map.of("missing", 1);

        assertThatThrownBy(() -> orderCreationService.createPendingOrder(userID, request, consolidatedItemMap, Map.of(), "idem-key"))
                .isInstanceOf(ProductNotFoundException.class);

        verifyNoInteractions(transactionRepository, outboxEventRepository);
    }

    // ---- createPendingOrder: outbox event ----

    @Test
    void createPendingOrder_writesOutboxEventWithOrderPlacedEventType() throws JsonProcessingException {
        UUID userID = UUID.randomUUID();
        UUID paymentMethodID = UUID.randomUUID();
        PlaceOrderRequest request = placeOrderRequest(UUID.randomUUID(), paymentMethodID, BigDecimal.ZERO, new OrderItemDTO(1, "p1"));
        Map<String, Integer> consolidatedItemMap = Map.of("p1", 1);
        Map<String, Product> productMap = Map.of("p1", product(BigDecimal.TEN));
        when(shippingService.getTotalShippingWeight(any(), eq(productMap))).thenReturn(BigDecimal.ZERO);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"eventType\":\"ORDER_PLACED\"}");

        Transaction result = orderCreationService.createPendingOrder(userID, request, consolidatedItemMap, productMap, "idem-key");

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        OutboxEvent outboxEvent = captor.getValue();
        assertThat(outboxEvent.getAggregateID()).isEqualTo(result.getOrderNumber());
        assertThat(outboxEvent.getTopic()).isEqualTo(ORDER_EVENTS);
        assertThat(outboxEvent.getPayload()).isEqualTo("{\"eventType\":\"ORDER_PLACED\"}");
        assertThat(outboxEvent.getIdempotencyKey()).isEqualTo("idem-key");
        assertThat(outboxEvent.getStatus()).isEqualTo("PENDING");
        assertThat(outboxEvent.isPublished()).isFalse();
        assertThat(outboxEvent.getRetryCount()).isZero();
        assertThat(outboxEvent.getCreatedOn()).isNotNull();
    }

    @Test
    void createPendingOrder_throwsOutboxEventCreationException_whenSerializationFails() throws JsonProcessingException {
        UUID userID = UUID.randomUUID();
        PlaceOrderRequest request = placeOrderRequest(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ZERO, new OrderItemDTO(1, "p1"));
        Map<String, Integer> consolidatedItemMap = Map.of("p1", 1);
        Map<String, Product> productMap = Map.of("p1", product(BigDecimal.TEN));
        when(shippingService.getTotalShippingWeight(any(), eq(productMap))).thenReturn(BigDecimal.ZERO);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("boom") {
        });

        assertThatThrownBy(() -> orderCreationService.createPendingOrder(userID, request, consolidatedItemMap, productMap, "idem-key"))
                .isInstanceOf(OutboxEventCreationException.class);
    }
}
