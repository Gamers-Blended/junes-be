package com.gamersblended.junes.service.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamersblended.junes.dto.event.InventoryChangedEvent;
import com.gamersblended.junes.exception.OutboxEventCreationException;
import com.gamersblended.junes.exception.ProductNotFoundException;
import com.gamersblended.junes.model.OutboxEvent;
import com.gamersblended.junes.model.Product;
import com.gamersblended.junes.repository.jpa.OutboxEventRepository;
import com.mongodb.client.result.UpdateResult;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static com.gamersblended.junes.constant.KafkaConstants.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private ObjectMapper objectMapper;

    private InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        inventoryService = new InventoryService(mongoTemplate, outboxEventRepository, objectMapper);
    }

    private static Product product(ObjectId id, int stock) {
        Product product = new Product("Game", "slug", "description", BigDecimal.TEN, "PS5", "US", "Standard",
                "publisher", LocalDate.now(), Set.of(), Set.of(), Set.of(), Set.of(), BigDecimal.ONE, 0, stock,
                "image.png", List.of(), LocalDate.now());
        product.setId(id);
        return product;
    }

    // ---- reserveStock ----

    @Test
    void reserveStock_returnsFalse_whenInsufficientStock() {
        ObjectId id = new ObjectId();
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Product.class)))
                .thenReturn(UpdateResult.acknowledged(1, 0L, null));

        boolean result = inventoryService.reserveStock(id.toHexString(), 2);

        assertThat(result).isFalse();
        verify(mongoTemplate, never()).findById(any(ObjectId.class), eq(Product.class));
        verifyNoInteractions(outboxEventRepository);
    }

    @Test
    void reserveStock_returnsTrue_andWritesOutboxEvent_onSuccess() throws Exception {
        ObjectId id = new ObjectId();
        Product updatedProduct = product(id, 8);
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Product.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));
        when(mongoTemplate.findById(id, Product.class)).thenReturn(updatedProduct);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        boolean result = inventoryService.reserveStock(id.toHexString(), 2);

        assertThat(result).isTrue();

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(objectMapper).writeValueAsString(eventCaptor.capture());
        InventoryChangedEvent event = (InventoryChangedEvent) eventCaptor.getValue();
        assertThat(event.getProductID()).isEqualTo(id.toHexString());
        assertThat(event.getPreviousStock()).isEqualTo(10);
        assertThat(event.getCurrentStock()).isEqualTo(8);
        assertThat(event.getQuantityChanged()).isEqualTo(-2);
        assertThat(event.getReason()).isEqualTo(ORDER_PLACED);

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getAggregateID()).isEqualTo(id.toHexString());
        assertThat(outboxCaptor.getValue().getTopic()).isEqualTo(INVENTORY_EVENTS);
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo(INVENTORY_CHANGED);
        assertThat(outboxCaptor.getValue().getStatus()).isEqualTo(PENDING);
    }

    @Test
    void reserveStock_throwsOutboxEventCreationException_whenSerializationFails() throws Exception {
        ObjectId id = new ObjectId();
        Product updatedProduct = product(id, 8);
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Product.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));
        when(mongoTemplate.findById(id, Product.class)).thenReturn(updatedProduct);
        when(objectMapper.writeValueAsString(any())).thenThrow(mock(com.fasterxml.jackson.core.JsonProcessingException.class));
        String productID = id.toHexString();

        assertThatThrownBy(() -> inventoryService.reserveStock(productID, 2))
                .isInstanceOf(OutboxEventCreationException.class);

        verify(outboxEventRepository, never()).save(any());
    }

    // ---- restoreStock ----

    @Test
    void restoreStock_throwsProductNotFoundException_whenProductMissing() {
        ObjectId id = new ObjectId();
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Product.class)))
                .thenReturn(null);
        String productID = id.toHexString();

        assertThatThrownBy(() -> inventoryService.restoreStock(productID, 3))
                .isInstanceOf(ProductNotFoundException.class);

        verifyNoInteractions(outboxEventRepository);
    }

    @Test
    void restoreStock_writesOutboxEvent_onSuccess() throws Exception {
        ObjectId id = new ObjectId();
        Product restoredProduct = product(id, 13);
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Product.class)))
                .thenReturn(restoredProduct);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        inventoryService.restoreStock(id.toHexString(), 3);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(objectMapper).writeValueAsString(eventCaptor.capture());
        InventoryChangedEvent event = (InventoryChangedEvent) eventCaptor.getValue();
        assertThat(event.getProductID()).isEqualTo(id.toHexString());
        assertThat(event.getPreviousStock()).isEqualTo(10);
        assertThat(event.getCurrentStock()).isEqualTo(13);
        assertThat(event.getQuantityChanged()).isEqualTo(3);
        assertThat(event.getReason()).isEqualTo(STOCK_RELEASED);

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getTopic()).isEqualTo(INVENTORY_EVENTS);
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo(INVENTORY_CHANGED);
    }

    @Test
    void restoreStock_throwsOutboxEventCreationException_whenSerializationFails() throws Exception {
        ObjectId id = new ObjectId();
        Product restoredProduct = product(id, 13);
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Product.class)))
                .thenReturn(restoredProduct);
        when(objectMapper.writeValueAsString(any())).thenThrow(mock(com.fasterxml.jackson.core.JsonProcessingException.class));
        String productID = id.toHexString();

        assertThatThrownBy(() -> inventoryService.restoreStock(productID, 3))
                .isInstanceOf(OutboxEventCreationException.class);
    }
}
