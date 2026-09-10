package com.gamersblended.junes.service.payment;

import com.gamersblended.junes.dto.event.PaymentFailedEvent;
import com.gamersblended.junes.dto.event.StripeEmailUpdateEvent;
import com.gamersblended.junes.dto.response.SetupIntentResponseDTO;
import com.gamersblended.junes.exception.StripeOperationException;
import com.gamersblended.junes.repository.jpa.ProcessedEventRepository;
import com.gamersblended.junes.repository.jpa.UserRepository;
import com.gamersblended.junes.util.KafkaEventParser;
import com.stripe.StripeClient;
import com.stripe.exception.ApiConnectionException;
import com.stripe.model.Customer;
import com.stripe.model.SetupIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.CustomerUpdateParams;
import com.stripe.param.SetupIntentCreateParams;
import com.stripe.service.CustomerService;
import com.stripe.service.SetupIntentService;
import com.stripe.service.V1Services;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.Optional;
import java.util.UUID;

import static com.gamersblended.junes.constant.KafkaConstants.STRIPE_SYNC_EVENTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StripeServiceTest {

    @Mock
    private StripeClient stripeClient;
    @Mock
    private KafkaEventParser kafkaEventParser;
    @Mock
    private ProcessedEventRepository processedEventRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private Acknowledgment ack;
    @Mock
    private V1Services v1Services;
    @Mock
    private CustomerService customerService;
    @Mock
    private SetupIntentService setupIntentService;

    private StripeService stripeService;

    @BeforeEach
    void setUp() {
        stripeService = new StripeService(stripeClient, kafkaEventParser, processedEventRepository, userRepository);
    }

    private static ConsumerRecord<String, String> consumerRecord() {
        return new ConsumerRecord<>(STRIPE_SYNC_EVENTS, 0, 0L, "key", "raw");
    }

    private static StripeEmailUpdateEvent emailUpdateEvent(UUID userID) {
        StripeEmailUpdateEvent event = new StripeEmailUpdateEvent();
        event.setEventID("evt-1");
        event.setUserID(userID);
        event.setStripeCustomerID("cus_1");
        event.setNewEmail("new@example.com");
        return event;
    }

    // ---- createCustomer ----

    @Test
    void createCustomer_returnsStripeCustomerID_onSuccess() throws Exception {
        UUID userID = UUID.randomUUID();
        Customer customer = new Customer();
        customer.setId("cus_new");
        when(stripeClient.v1()).thenReturn(v1Services);
        when(v1Services.customers()).thenReturn(customerService);
        when(customerService.create(any(CustomerCreateParams.class), any(RequestOptions.class)))
                .thenReturn(customer);

        String result = stripeService.createCustomer(userID, "user@example.com");

        assertThat(result).isEqualTo("cus_new");
    }

    @Test
    void createCustomer_usesUserIDBasedIdempotencyKey() throws Exception {
        UUID userID = UUID.randomUUID();
        Customer customer = new Customer();
        customer.setId("cus_new");
        when(stripeClient.v1()).thenReturn(v1Services);
        when(v1Services.customers()).thenReturn(customerService);
        when(customerService.create(any(CustomerCreateParams.class), any(RequestOptions.class)))
                .thenReturn(customer);

        stripeService.createCustomer(userID, "user@example.com");

        ArgumentCaptor<RequestOptions> optionsCaptor = ArgumentCaptor.forClass(RequestOptions.class);
        ArgumentCaptor<CustomerCreateParams> paramsCaptor = ArgumentCaptor.forClass(CustomerCreateParams.class);
        verify(customerService).create(paramsCaptor.capture(), optionsCaptor.capture());
        assertThat(optionsCaptor.getValue().getIdempotencyKey()).isEqualTo("create-customer-" + userID);
        assertThat(paramsCaptor.getValue().getEmail()).isEqualTo("user@example.com");
    }

    @Test
    void createCustomer_throwsStripeOperationException_whenStripeCallFails() throws Exception {
        UUID userID = UUID.randomUUID();
        when(stripeClient.v1()).thenReturn(v1Services);
        when(v1Services.customers()).thenReturn(customerService);
        when(customerService.create(any(CustomerCreateParams.class), any(RequestOptions.class)))
                .thenThrow(new ApiConnectionException("stripe down"));

        assertThatThrownBy(() -> stripeService.createCustomer(userID, "user@example.com"))
                .isInstanceOf(StripeOperationException.class)
                .hasMessageContaining(userID.toString());
    }

    // ---- onStripeEmailUpdateRequested: dispatch / idempotency ----

    @Test
    void onStripeEmailUpdateRequested_acknowledgesAndSkips_whenParsedEventIsWrongType() {
        when(kafkaEventParser.parse("raw")).thenReturn(new PaymentFailedEvent());

        stripeService.onStripeEmailUpdateRequested(consumerRecord(), ack);

        verify(ack).acknowledge();
        verifyNoInteractions(stripeClient, processedEventRepository);
    }

    @Test
    void onStripeEmailUpdateRequested_acknowledgesAndSkips_whenEventAlreadyProcessed() {
        StripeEmailUpdateEvent event = emailUpdateEvent(UUID.randomUUID());
        when(kafkaEventParser.parse("raw")).thenReturn(event);
        when(processedEventRepository.existsByEventID("evt-1")).thenReturn(true);

        stripeService.onStripeEmailUpdateRequested(consumerRecord(), ack);

        verify(ack).acknowledge();
        verifyNoInteractions(stripeClient);
    }

    // ---- onStripeEmailUpdateRequested: Stripe call ----

    @Test
    void onStripeEmailUpdateRequested_updatesCustomerEmail_whenNotYetProcessed() throws Exception {
        StripeEmailUpdateEvent event = emailUpdateEvent(UUID.randomUUID());
        when(kafkaEventParser.parse("raw")).thenReturn(event);
        when(processedEventRepository.existsByEventID("evt-1")).thenReturn(false);
        when(stripeClient.v1()).thenReturn(v1Services);
        when(v1Services.customers()).thenReturn(customerService);
        when(customerService.update(eq("cus_1"), any(CustomerUpdateParams.class), any(RequestOptions.class)))
                .thenReturn(new Customer());

        stripeService.onStripeEmailUpdateRequested(consumerRecord(), ack);

        ArgumentCaptor<CustomerUpdateParams> paramsCaptor = ArgumentCaptor.forClass(CustomerUpdateParams.class);
        ArgumentCaptor<RequestOptions> optionsCaptor = ArgumentCaptor.forClass(RequestOptions.class);
        verify(customerService).update(eq("cus_1"), paramsCaptor.capture(), optionsCaptor.capture());
        assertThat(paramsCaptor.getValue().getEmail()).isEqualTo("new@example.com");
        assertThat(optionsCaptor.getValue().getIdempotencyKey()).isEqualTo("update-customer-email-evt-1");
    }

    @Test
    void onStripeEmailUpdateRequested_throwsStripeOperationException_whenUpdateFails() throws Exception {
        StripeEmailUpdateEvent event = emailUpdateEvent(UUID.randomUUID());
        when(kafkaEventParser.parse("raw")).thenReturn(event);
        when(processedEventRepository.existsByEventID("evt-1")).thenReturn(false);
        when(stripeClient.v1()).thenReturn(v1Services);
        when(v1Services.customers()).thenReturn(customerService);
        when(customerService.update(eq("cus_1"), any(CustomerUpdateParams.class), any(RequestOptions.class)))
                .thenThrow(new ApiConnectionException("stripe down"));

        ConsumerRecord<String, String> consumerRecord = consumerRecord();
        assertThatThrownBy(() -> stripeService.onStripeEmailUpdateRequested(consumerRecord, ack))
                .isInstanceOf(StripeOperationException.class)
                .hasMessageContaining("cus_1");

        verify(ack, never()).acknowledge();
    }

    // ---- createSetupIntent ----

    @Test
    void createSetupIntent_returnsClientSecret_onSuccess() throws Exception {
        UUID userID = UUID.randomUUID();
        SetupIntent setupIntent = new SetupIntent();
        setupIntent.setClientSecret("seti_secret_1");
        when(userRepository.getStripeCustomerID(userID)).thenReturn(Optional.of("cus_1"));
        when(stripeClient.v1()).thenReturn(v1Services);
        when(v1Services.setupIntents()).thenReturn(setupIntentService);
        when(setupIntentService.create(any(SetupIntentCreateParams.class))).thenReturn(setupIntent);

        SetupIntentResponseDTO result = stripeService.createSetupIntent(userID);

        assertThat(result.getClientSecret()).isEqualTo("seti_secret_1");
    }

    @Test
    void createSetupIntent_throwsStripeOperationException_whenStripeCustomerIDNotFound() {
        UUID userID = UUID.randomUUID();
        when(userRepository.getStripeCustomerID(userID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stripeService.createSetupIntent(userID))
                .isInstanceOf(StripeOperationException.class);

        verifyNoInteractions(stripeClient);
    }

    @Test
    void createSetupIntent_propagatesStripeException_whenCreateFails() throws Exception {
        UUID userID = UUID.randomUUID();
        when(userRepository.getStripeCustomerID(userID)).thenReturn(Optional.of("cus_1"));
        when(stripeClient.v1()).thenReturn(v1Services);
        when(v1Services.setupIntents()).thenReturn(setupIntentService);
        when(setupIntentService.create(any(SetupIntentCreateParams.class)))
                .thenThrow(new ApiConnectionException("stripe down"));

        assertThatThrownBy(() -> stripeService.createSetupIntent(userID))
                .isInstanceOf(ApiConnectionException.class);
    }
}
