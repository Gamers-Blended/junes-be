package com.gamersblended.junes.controller;

import com.gamersblended.junes.WebMvcTestApplication;
import com.gamersblended.junes.model.DeadLetterEvent;
import com.gamersblended.junes.model.OutboxEvent;
import com.gamersblended.junes.service.outbox.ReconciliationService;
import com.gamersblended.junes.util.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @WebMvcTest + MockMvc slice test for {@link ReconciliationController}, replacing the previous
 * plain-Mockito style — see {@link AuthControllerWebMvcTest} for the pattern rationale.
 * <p>
 * {@code /junes/api/v1/housekeep/reconciliation/**} requires {@code hasRole("ADMIN")} in
 * {@link com.gamersblended.junes.config.SecurityConfig}, but {@code addFilters = false} bypasses the
 * whole security filter chain including that check. This is a deliberate scope boundary, not an
 * oversight: the controller itself performs no authorization logic (enforcement is purely path-based
 * in {@code SecurityConfig}), so the ADMIN gate was equally untestable in the plain-Mockito version
 * this replaces — this class targets request/response behaviour, not authentication.
 */
@WebMvcTest(ReconciliationController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = WebMvcTestApplication.class)
class ReconciliationControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReconciliationService reconciliationService;

    // JwtAuthenticationFilter is a @Component Filter, so @WebMvcTest instantiates it even with
    // addFilters = false; it needs a JwtUtils bean to construct, which this slice otherwise wouldn't provide.
    @MockBean
    private JwtUtils jwtUtils;

    private static OutboxEvent outboxEvent() {
        OutboxEvent event = new OutboxEvent();
        event.setId(UUID.randomUUID());
        event.setAggregateID("order-1");
        event.setEventType("OrderCreated");
        event.setTopic("order-events");
        event.setPayload("{}");
        event.setStatus("FAILED_PERMANENTLY");
        event.setCreatedOn(LocalDateTime.now());
        event.setRetryCount(5);
        return event;
    }

    private static DeadLetterEvent deadLetterEvent() {
        DeadLetterEvent event = new DeadLetterEvent();
        event.setId(UUID.randomUUID());
        event.setOriginalTopic("order-events");
        event.setEventID("evt-1");
        event.setEventType("OrderCreated");
        event.setPayload("{}");
        event.setStatus("UNRESOLVED");
        event.setFailedOn(LocalDateTime.now());
        return event;
    }

    @Test
    void getPermanentlyFailedOutboxEvents_returnsListFromService() throws Exception {
        List<OutboxEvent> events = List.of(outboxEvent());
        when(reconciliationService.getPermanentlyFailedOutboxEvents()).thenReturn(events);

        mockMvc.perform(get("/junes/api/v1/housekeep/reconciliation/failed-outbox-events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].aggregateID").value("order-1"))
                .andExpect(jsonPath("$[0].status").value("FAILED_PERMANENTLY"));

        verify(reconciliationService).getPermanentlyFailedOutboxEvents();
    }

    @Test
    void getPermanentlyFailedOutboxEvents_returnsEmptyList_whenNoneFailed() throws Exception {
        when(reconciliationService.getPermanentlyFailedOutboxEvents()).thenReturn(List.of());

        mockMvc.perform(get("/junes/api/v1/housekeep/reconciliation/failed-outbox-events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void getUnresolvedDeadLetterEvents_returnsListFromService() throws Exception {
        List<DeadLetterEvent> events = List.of(deadLetterEvent());
        when(reconciliationService.getUnresolvedDeadLetterEvents()).thenReturn(events);

        mockMvc.perform(get("/junes/api/v1/housekeep/reconciliation/dead-letter-events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].eventID").value("evt-1"))
                .andExpect(jsonPath("$[0].status").value("UNRESOLVED"));

        verify(reconciliationService).getUnresolvedDeadLetterEvents();
    }

    @Test
    void getUnresolvedDeadLetterEvents_returnsEmptyList_whenNoneUnresolved() throws Exception {
        when(reconciliationService.getUnresolvedDeadLetterEvents()).thenReturn(List.of());

        mockMvc.perform(get("/junes/api/v1/housekeep/reconciliation/dead-letter-events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
