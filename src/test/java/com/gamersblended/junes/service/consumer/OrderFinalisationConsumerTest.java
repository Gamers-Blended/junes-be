package com.gamersblended.junes.service.consumer;

import com.gamersblended.junes.constant.TransactionStatus;
import com.gamersblended.junes.dto.AddressDTO;
import com.gamersblended.junes.dto.event.PaymentFailedEvent;
import com.gamersblended.junes.dto.event.PaymentSucceededEvent;
import com.gamersblended.junes.dto.event.StripePaymentMethodDetachEvent;
import com.gamersblended.junes.exception.EmailNotFoundException;
import com.gamersblended.junes.exception.SavedItemNotFoundException;
import com.gamersblended.junes.exception.TransactionNotFoundException;
import com.gamersblended.junes.mapper.AddressMapper;
import com.gamersblended.junes.model.Address;
import com.gamersblended.junes.model.Transaction;
import com.gamersblended.junes.model.TransactionItem;
import com.gamersblended.junes.repository.jpa.AddressRepository;
import com.gamersblended.junes.repository.jpa.ProcessedEventRepository;
import com.gamersblended.junes.repository.jpa.TransactionRepository;
import com.gamersblended.junes.repository.jpa.UserRepository;
import com.gamersblended.junes.service.cache.OrderHistoryCacheService;
import com.gamersblended.junes.service.cart.CartService;
import com.gamersblended.junes.service.email.EmailProducerService;
import com.gamersblended.junes.service.order.TransactionService;
import com.gamersblended.junes.service.product.InventoryService;
import com.gamersblended.junes.util.KafkaEventParser;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.gamersblended.junes.constant.KafkaConstants.ORDER_EVENTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.argThat;

@ExtendWith(MockitoExtension.class)
class OrderFinalisationConsumerTest {

    @Mock
    private KafkaEventParser kafkaEventParser;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private ProcessedEventRepository processedEventRepository;
    @Mock
    private InventoryService inventoryService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AddressRepository addressRepository;
    @Mock
    private AddressMapper addressMapper;
    @Mock
    private TransactionService transactionService;
    @Mock
    private EmailProducerService emailProducerService;
    @Mock
    private OrderHistoryCacheService orderHistoryCacheService;
    @Mock
    private CartService cartService;
    @Mock
    private Acknowledgment ack;

    private OrderFinalisationConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new OrderFinalisationConsumer(
                kafkaEventParser, transactionRepository, processedEventRepository, inventoryService,
                userRepository, addressRepository, addressMapper, transactionService, emailProducerService,
                orderHistoryCacheService, cartService);
    }

    private static ConsumerRecord<String, String> consumerRecord() {
        return new ConsumerRecord<>(ORDER_EVENTS, 0, 0L, "key", "raw");
    }

    private static Transaction transaction(UUID userID, String orderNumber, TransactionItem... items) {
        Transaction transaction = new Transaction();
        transaction.setUserID(userID);
        transaction.setOrderNumber(orderNumber);
        transaction.setShippingAddressID(UUID.randomUUID());
        transaction.setItems(List.of(items));
        return transaction;
    }

    private static TransactionItem transactionItem(String productID, int quantity) {
        TransactionItem item = new TransactionItem();
        item.setProductID(productID);
        item.setQuantity(quantity);
        return item;
    }

    // ---- onOrderEvent: dispatch ----

    @Test
    void onOrderEvent_dispatchesToHandlePaymentSucceeded_whenPaymentSucceededEvent() {
        UUID userID = UUID.randomUUID();
        PaymentSucceededEvent event = new PaymentSucceededEvent();
        event.setEventID("evt-1");
        event.setUserID(userID);
        event.setOrderNumber("ORD-1");
        when(kafkaEventParser.parse("raw")).thenReturn(event);
        when(processedEventRepository.existsByEventID("evt-1")).thenReturn(true);

        consumer.onOrderEvent(consumerRecord(), ack);

        verify(processedEventRepository).existsByEventID("evt-1");
        verify(ack).acknowledge();
    }

    @Test
    void onOrderEvent_dispatchesToHandlePaymentFailed_whenPaymentFailedEvent() {
        PaymentFailedEvent event = new PaymentFailedEvent();
        event.setEventID("evt-2");
        when(kafkaEventParser.parse("raw")).thenReturn(event);
        when(processedEventRepository.existsByEventID("evt-2")).thenReturn(true);

        consumer.onOrderEvent(consumerRecord(), ack);

        verify(processedEventRepository).existsByEventID("evt-2");
        verify(ack).acknowledge();
    }

    @Test
    void onOrderEvent_acknowledgesAndSkips_whenEventIsUnhandledType() {
        StripePaymentMethodDetachEvent unrelated = new StripePaymentMethodDetachEvent();
        when(kafkaEventParser.parse("raw")).thenReturn(unrelated);

        consumer.onOrderEvent(consumerRecord(), ack);

        verify(ack).acknowledge();
        verifyNoInteractions(transactionRepository, processedEventRepository);
    }

    // ---- handlePaymentSucceeded ----

    @Test
    void handlePaymentSucceeded_skips_whenEventAlreadyProcessed() {
        PaymentSucceededEvent event = new PaymentSucceededEvent();
        event.setEventID("evt-1");
        event.setUserID(UUID.randomUUID());
        event.setOrderNumber("ORD-1");
        when(kafkaEventParser.parse("raw")).thenReturn(event);
        when(processedEventRepository.existsByEventID("evt-1")).thenReturn(true);

        consumer.onOrderEvent(consumerRecord(), ack);

        verifyNoInteractions(transactionRepository, emailProducerService, orderHistoryCacheService, cartService);
        verify(ack).acknowledge();
    }

    @Test
    void handlePaymentSucceeded_throwsTransactionNotFoundException_whenTransactionMissing() {
        UUID userID = UUID.randomUUID();
        PaymentSucceededEvent event = new PaymentSucceededEvent();
        event.setEventID("evt-1");
        event.setUserID(userID);
        event.setOrderNumber("ORD-1");
        when(kafkaEventParser.parse("raw")).thenReturn(event);
        when(processedEventRepository.existsByEventID("evt-1")).thenReturn(false);
        when(transactionRepository.findByUserIDAndOrderNumber(userID, "ORD-1")).thenReturn(Optional.empty());

        ConsumerRecord<String, String> consumerRecord = consumerRecord();
        assertThatThrownBy(() -> consumer.onOrderEvent(consumerRecord, ack))
                .isInstanceOf(TransactionNotFoundException.class)
                .hasMessageContaining("ORD-1");

        verify(ack, never()).acknowledge();
    }

    @Test
    void handlePaymentSucceeded_marksAwaitingShipment_sendsEmail_evictsCache_clearsCart_andMarksProcessed() {
        UUID userID = UUID.randomUUID();
        PaymentSucceededEvent event = new PaymentSucceededEvent();
        event.setEventID("evt-1");
        event.setUserID(userID);
        event.setOrderNumber("ORD-1");
        Transaction transaction = transaction(userID, "ORD-1", transactionItem("prod-1", 2));

        when(kafkaEventParser.parse("raw")).thenReturn(event);
        when(processedEventRepository.existsByEventID("evt-1")).thenReturn(false);
        when(transactionRepository.findByUserIDAndOrderNumber(userID, "ORD-1")).thenReturn(Optional.of(transaction));
        when(userRepository.getUserEmail(userID)).thenReturn(Optional.of("user@example.com"));
        when(transactionService.getProductsByIDMap(anyList(), any())).thenReturn(Map.of());
        Address address = new Address();
        when(addressRepository.getAddressByUserIDAndID(eq(userID), any())).thenReturn(Optional.of(address));
        when(addressMapper.toDTO(address)).thenReturn(new AddressDTO());
        when(cartService.deleteCart(userID, null)).thenReturn(true);

        consumer.onOrderEvent(consumerRecord(), ack);

        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(transactionCaptor.capture());
        assertThat(transactionCaptor.getValue().getStatus()).isEqualTo(TransactionStatus.AWAITING_SHIPMENT.getTransactionStatusValue());

        verify(emailProducerService).sendOrderConfirmedEmail(eq("user@example.com"), eq(transaction), any(), any());
        verify(orderHistoryCacheService).evict(userID);
        verify(cartService).deleteCart(userID, null);
        verify(processedEventRepository).save(argThat(pe -> pe.getEventID().equals("evt-1")));
        verify(ack).acknowledge();
    }

    @Test
    void handlePaymentSucceeded_throwsEmailNotFoundException_whenUserEmailMissing() {
        UUID userID = UUID.randomUUID();
        PaymentSucceededEvent event = new PaymentSucceededEvent();
        event.setEventID("evt-1");
        event.setUserID(userID);
        event.setOrderNumber("ORD-1");
        Transaction transaction = transaction(userID, "ORD-1", transactionItem("prod-1", 2));

        when(kafkaEventParser.parse("raw")).thenReturn(event);
        when(processedEventRepository.existsByEventID("evt-1")).thenReturn(false);
        when(transactionRepository.findByUserIDAndOrderNumber(userID, "ORD-1")).thenReturn(Optional.of(transaction));
        when(userRepository.getUserEmail(userID)).thenReturn(Optional.empty());

        ConsumerRecord<String, String> consumerRecord = consumerRecord();
        assertThatThrownBy(() -> consumer.onOrderEvent(consumerRecord, ack))
                .isInstanceOf(EmailNotFoundException.class);

        verify(ack, never()).acknowledge();
        verify(orderHistoryCacheService, never()).evict(any());
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    void handlePaymentSucceeded_throwsSavedItemNotFoundException_whenShippingAddressMissing() {
        UUID userID = UUID.randomUUID();
        PaymentSucceededEvent event = new PaymentSucceededEvent();
        event.setEventID("evt-1");
        event.setUserID(userID);
        event.setOrderNumber("ORD-1");
        Transaction transaction = transaction(userID, "ORD-1", transactionItem("prod-1", 2));

        when(kafkaEventParser.parse("raw")).thenReturn(event);
        when(processedEventRepository.existsByEventID("evt-1")).thenReturn(false);
        when(transactionRepository.findByUserIDAndOrderNumber(userID, "ORD-1")).thenReturn(Optional.of(transaction));
        when(userRepository.getUserEmail(userID)).thenReturn(Optional.of("user@example.com"));
        when(transactionService.getProductsByIDMap(anyList(), any())).thenReturn(Map.of());
        when(addressRepository.getAddressByUserIDAndID(eq(userID), any())).thenReturn(Optional.empty());

        ConsumerRecord<String, String> consumerRecord = consumerRecord();
        assertThatThrownBy(() -> consumer.onOrderEvent(consumerRecord, ack))
                .isInstanceOf(SavedItemNotFoundException.class);

        verify(ack, never()).acknowledge();
        verify(orderHistoryCacheService, never()).evict(any());
    }

    @Test
    void handlePaymentSucceeded_logsWarn_whenCartAlreadyCleared() {
        UUID userID = UUID.randomUUID();
        PaymentSucceededEvent event = new PaymentSucceededEvent();
        event.setEventID("evt-1");
        event.setUserID(userID);
        event.setOrderNumber("ORD-1");
        Transaction transaction = transaction(userID, "ORD-1", transactionItem("prod-1", 2));

        when(kafkaEventParser.parse("raw")).thenReturn(event);
        when(processedEventRepository.existsByEventID("evt-1")).thenReturn(false);
        when(transactionRepository.findByUserIDAndOrderNumber(userID, "ORD-1")).thenReturn(Optional.of(transaction));
        when(userRepository.getUserEmail(userID)).thenReturn(Optional.of("user@example.com"));
        when(transactionService.getProductsByIDMap(anyList(), any())).thenReturn(Map.of());
        when(addressRepository.getAddressByUserIDAndID(eq(userID), any())).thenReturn(Optional.of(new Address()));
        when(addressMapper.toDTO(any())).thenReturn(new AddressDTO());
        when(cartService.deleteCart(userID, null)).thenReturn(false);

        consumer.onOrderEvent(consumerRecord(), ack);

        verify(cartService).deleteCart(userID, null);
        verify(processedEventRepository).save(any());
        verify(ack).acknowledge();
    }

    // ---- handlePaymentFailed ----

    @Test
    void handlePaymentFailed_skips_whenEventAlreadyProcessed() {
        PaymentFailedEvent event = new PaymentFailedEvent();
        event.setEventID("evt-3");
        event.setUserID(UUID.randomUUID());
        event.setOrderNumber("ORD-2");
        when(kafkaEventParser.parse("raw")).thenReturn(event);
        when(processedEventRepository.existsByEventID("evt-3")).thenReturn(true);

        consumer.onOrderEvent(consumerRecord(), ack);

        verifyNoInteractions(transactionRepository, inventoryService);
        verify(ack).acknowledge();
    }

    @Test
    void handlePaymentFailed_throwsTransactionNotFoundException_whenTransactionMissing() {
        UUID userID = UUID.randomUUID();
        PaymentFailedEvent event = new PaymentFailedEvent();
        event.setEventID("evt-3");
        event.setUserID(userID);
        event.setOrderNumber("ORD-2");
        when(kafkaEventParser.parse("raw")).thenReturn(event);
        when(processedEventRepository.existsByEventID("evt-3")).thenReturn(false);
        when(transactionRepository.findByUserIDAndOrderNumber(userID, "ORD-2")).thenReturn(Optional.empty());

        ConsumerRecord<String, String> consumerRecord = consumerRecord();
        assertThatThrownBy(() -> consumer.onOrderEvent(consumerRecord, ack))
                .isInstanceOf(TransactionNotFoundException.class);

        verify(ack, never()).acknowledge();
    }

    @Test
    void handlePaymentFailed_marksPaymentFailed_restoresStockForEachItem_andMarksProcessed() {
        UUID userID = UUID.randomUUID();
        PaymentFailedEvent event = new PaymentFailedEvent();
        event.setEventID("evt-3");
        event.setUserID(userID);
        event.setOrderNumber("ORD-2");
        event.setFailureReason("card_declined");
        Transaction transaction = transaction(userID, "ORD-2",
                transactionItem("prod-1", 2), transactionItem("prod-2", 1));

        when(kafkaEventParser.parse("raw")).thenReturn(event);
        when(processedEventRepository.existsByEventID("evt-3")).thenReturn(false);
        when(transactionRepository.findByUserIDAndOrderNumber(userID, "ORD-2")).thenReturn(Optional.of(transaction));

        consumer.onOrderEvent(consumerRecord(), ack);

        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(transactionCaptor.capture());
        assertThat(transactionCaptor.getValue().getStatus()).isEqualTo(TransactionStatus.PAYMENT_FAILED.getTransactionStatusValue());

        verify(inventoryService).restoreStock("prod-1", 2);
        verify(inventoryService).restoreStock("prod-2", 1);
        verify(processedEventRepository).save(argThat(pe -> pe.getEventID().equals("evt-3")));
        verify(ack).acknowledge();
    }

    @Test
    void handlePaymentFailed_continuesRestoringRemainingItems_whenOneRestoreFails() {
        UUID userID = UUID.randomUUID();
        PaymentFailedEvent event = new PaymentFailedEvent();
        event.setEventID("evt-3");
        event.setUserID(userID);
        event.setOrderNumber("ORD-2");
        Transaction transaction = transaction(userID, "ORD-2",
                transactionItem("prod-1", 2), transactionItem("prod-2", 1));

        when(kafkaEventParser.parse("raw")).thenReturn(event);
        when(processedEventRepository.existsByEventID("evt-3")).thenReturn(false);
        when(transactionRepository.findByUserIDAndOrderNumber(userID, "ORD-2")).thenReturn(Optional.of(transaction));
        doThrow(new RuntimeException("mongo down")).when(inventoryService).restoreStock("prod-1", 2);

        consumer.onOrderEvent(consumerRecord(), ack);

        verify(inventoryService).restoreStock("prod-1", 2);
        verify(inventoryService).restoreStock("prod-2", 1);
        verify(processedEventRepository).save(any());
        verify(ack).acknowledge();
    }
}
