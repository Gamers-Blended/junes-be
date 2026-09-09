package com.gamersblended.junes.service.cart;

import com.gamersblended.junes.dto.OrderItemDTO;
import com.gamersblended.junes.exception.InvalidProductIdException;
import com.gamersblended.junes.exception.NegativeWeightException;
import com.gamersblended.junes.model.Product;
import com.gamersblended.junes.service.order.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShippingServiceTest {

    @Mock
    private TransactionService transactionService;

    private ShippingService shippingService;

    @BeforeEach
    void setUp() {
        shippingService = new ShippingService(transactionService);
    }

    private static Product productWithWeight(BigDecimal weight) {
        return new Product("name", "slug", "description", BigDecimal.TEN, "platform", "region", "edition",
                "publisher", LocalDate.now(), Set.of(), Set.of(), Set.of(), Set.of(), weight, 0, 0,
                "image.png", List.of(), LocalDate.now());
    }

    private static OrderItemDTO orderItem(String productID, int quantity) {
        return new OrderItemDTO(quantity, productID);
    }

    // ---- getShippingFee ----

    @Test
    void getShippingFee_returnsZero_whenListIsNull() {
        assertThat(shippingService.getShippingFee(null)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getShippingFee_returnsZero_whenListIsEmpty() {
        assertThat(shippingService.getShippingFee(List.of())).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getShippingFee_returnsZero_whenTotalWeightIsZero() {
        List<OrderItemDTO> items = List.of(orderItem(null, 3));

        assertThat(shippingService.getShippingFee(items)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getShippingFee_throwsNegativeWeightException_whenTotalWeightNegative() {
        List<OrderItemDTO> items = List.of(orderItem("p1", -1));
        when(transactionService.getProductsByIDMap(eq(items), any()))
                .thenReturn(Map.of("p1", productWithWeight(BigDecimal.valueOf(2))));

        assertThatThrownBy(() -> shippingService.getShippingFee(items))
                .isInstanceOf(NegativeWeightException.class);
    }

    @Test
    void getShippingFee_returnsFiveDollars_whenWeightUpToOneKg() {
        List<OrderItemDTO> items = List.of(orderItem("p1", 1));
        when(transactionService.getProductsByIDMap(eq(items), any()))
                .thenReturn(Map.of("p1", productWithWeight(BigDecimal.ONE)));

        assertThat(shippingService.getShippingFee(items)).isEqualByComparingTo(BigDecimal.valueOf(5.00));
    }

    @Test
    void getShippingFee_returnsSevenDollars_whenWeightUpToFiveKg() {
        List<OrderItemDTO> items = List.of(orderItem("p1", 1));
        when(transactionService.getProductsByIDMap(eq(items), any()))
                .thenReturn(Map.of("p1", productWithWeight(BigDecimal.valueOf(5))));

        assertThat(shippingService.getShippingFee(items)).isEqualByComparingTo(BigDecimal.valueOf(7.00));
    }

    @Test
    void getShippingFee_returnsTenDollars_whenWeightUpToTenKg() {
        List<OrderItemDTO> items = List.of(orderItem("p1", 1));
        when(transactionService.getProductsByIDMap(eq(items), any()))
                .thenReturn(Map.of("p1", productWithWeight(BigDecimal.valueOf(10))));

        assertThat(shippingService.getShippingFee(items)).isEqualByComparingTo(BigDecimal.valueOf(10.00));
    }

    @Test
    void getShippingFee_returnsFifteenDollars_whenWeightAboveTenKg() {
        List<OrderItemDTO> items = List.of(orderItem("p1", 1));
        when(transactionService.getProductsByIDMap(eq(items), any()))
                .thenReturn(Map.of("p1", productWithWeight(BigDecimal.valueOf(10.01))));

        assertThat(shippingService.getShippingFee(items)).isEqualByComparingTo(BigDecimal.valueOf(15.00));
    }

    // ---- getTotalShippingWeight(List, Map) ----

    @Test
    void getTotalShippingWeight_throwsInvalidProductIdException_whenProductMissingFromMap() {
        List<OrderItemDTO> items = List.of(orderItem("missing-id", 1));

        assertThatThrownBy(() -> shippingService.getTotalShippingWeight(items, Map.of()))
                .isInstanceOf(InvalidProductIdException.class);
    }

    @Test
    void getTotalShippingWeight_throwsInvalidProductIdException_whenProductWeightIsNull() {
        Product product = productWithWeight(BigDecimal.ONE);
        ReflectionTestUtils.setField(product, "weight", null);
        List<OrderItemDTO> items = List.of(orderItem("p1", 1));
        Map<String, Product> productMap = Map.of("p1", product);

        assertThatThrownBy(() -> shippingService.getTotalShippingWeight(items, productMap))
                .isInstanceOf(InvalidProductIdException.class);
    }

    @Test
    void getTotalShippingWeight_throwsInvalidProductIdException_whenProductWeightIsZeroOrNegative() {
        List<OrderItemDTO> items = List.of(orderItem("p1", 1));
        Map<String, Product> productMap = Map.of("p1", productWithWeight(BigDecimal.ZERO));

        assertThatThrownBy(() -> shippingService.getTotalShippingWeight(items, productMap))
                .isInstanceOf(InvalidProductIdException.class);
    }

    @Test
    void getTotalShippingWeight_sumsWeightAcrossItems() {
        List<OrderItemDTO> items = List.of(orderItem("p1", 2), orderItem("p2", 3));
        Map<String, Product> productMap = Map.of(
                "p1", productWithWeight(BigDecimal.valueOf(1.5)),
                "p2", productWithWeight(BigDecimal.valueOf(2.0)));

        BigDecimal totalWeight = shippingService.getTotalShippingWeight(items, productMap);

        // (1.5 * 2) + (2.0 * 3) = 3 + 6 = 9
        assertThat(totalWeight).isEqualByComparingTo(BigDecimal.valueOf(9));
    }

    @Test
    void getTotalShippingWeight_ignoresItemsWithNullProductID() {
        List<OrderItemDTO> items = List.of(orderItem(null, 5));

        BigDecimal totalWeight = shippingService.getTotalShippingWeight(items, Map.of());

        assertThat(totalWeight).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ---- getTotalShippingWeight(List) ----

    @Test
    void getTotalShippingWeight_delegatesToTransactionServiceForProductMetadata() {
        List<OrderItemDTO> items = List.of(orderItem("p1", 2));
        when(transactionService.getProductsByIDMap(eq(items), any()))
                .thenReturn(Map.of("p1", productWithWeight(BigDecimal.valueOf(3))));

        BigDecimal totalWeight = shippingService.getTotalShippingWeight(items);

        assertThat(totalWeight).isEqualByComparingTo(BigDecimal.valueOf(6));
    }
}
