package com.gamersblended.junes.service.order;

import com.gamersblended.junes.dto.request.PlaceOrderRequest;
import com.gamersblended.junes.util.IdempotentUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.function.Supplier;

import static com.gamersblended.junes.constant.KafkaConstants.ORDER_CREATED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderProcessingService orderProcessingService;
    @Mock
    private IdempotentUtils idempotentUtils;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderProcessingService, idempotentUtils);
    }

    // ---- placeOrder ----

    @Test
    void placeOrder_delegatesToIdempotentUtils_withOrderCreatedEventType() {
        UUID userID = UUID.randomUUID();
        PlaceOrderRequest request = new PlaceOrderRequest();
        when(idempotentUtils.executeIdempotent(eq(userID), eq(ORDER_CREATED), eq("idem-key"), eq(String.class), any()))
                .thenReturn("J123456789");

        String orderNumber = orderService.placeOrder(userID, request, "idem-key");

        assertThat(orderNumber).isEqualTo("J123456789");
        verify(idempotentUtils).executeIdempotent(eq(userID), eq(ORDER_CREATED), eq("idem-key"), eq(String.class), any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void placeOrder_suppliedActionInvokesOrderProcessingService() {
        UUID userID = UUID.randomUUID();
        PlaceOrderRequest request = new PlaceOrderRequest();
        ArgumentCaptor<Supplier<String>> supplierCaptor = ArgumentCaptor.forClass(Supplier.class);
        when(idempotentUtils.executeIdempotent(eq(userID), eq(ORDER_CREATED), eq("idem-key"), eq(String.class), supplierCaptor.capture()))
                .thenReturn("J123456789");
        when(orderProcessingService.processOrder(userID, request, "idem-key")).thenReturn("J123456789");

        orderService.placeOrder(userID, request, "idem-key");

        String result = supplierCaptor.getValue().get();
        assertThat(result).isEqualTo("J123456789");
        verify(orderProcessingService).processOrder(userID, request, "idem-key");
    }
}
