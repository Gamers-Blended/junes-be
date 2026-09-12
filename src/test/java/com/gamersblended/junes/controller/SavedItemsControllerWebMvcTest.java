package com.gamersblended.junes.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamersblended.junes.WebMvcTestApplication;
import com.gamersblended.junes.dto.AddressDTO;
import com.gamersblended.junes.dto.PaymentMethodDTO;
import com.gamersblended.junes.dto.request.AddPaymentMethodRequest;
import com.gamersblended.junes.dto.request.AttachAddressToPaymentMethodRequest;
import com.gamersblended.junes.dto.request.EditPaymentMethodRequest;
import com.gamersblended.junes.dto.request.SetAsDefaultRequest;
import com.gamersblended.junes.dto.response.SetupIntentResponseDTO;
import com.gamersblended.junes.service.auth.AccessTokenService;
import com.gamersblended.junes.service.payment.SavedItemsService;
import com.gamersblended.junes.service.payment.StripeService;
import com.gamersblended.junes.util.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @WebMvcTest slice test for {@link SavedItemsController} — see {@link AuthControllerWebMvcTest} for the
 * pattern rationale. This route falls under the temporary {@code /junes/api/v1/**} permitAll catch-all
 * in {@link com.gamersblended.junes.config.SecurityConfig} (rather than a dedicated rule), which is what
 * makes {@code addFilters = false} a safe stand-in for real behaviour here today.
 */
@WebMvcTest(SavedItemsController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = WebMvcTestApplication.class)
class SavedItemsControllerWebMvcTest {

    private static final String AUTH_HEADER = "Bearer token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SavedItemsService savedItemsService;

    @MockBean
    private AccessTokenService accessTokenService;

    @MockBean
    private StripeService stripeService;

    // JwtAuthenticationFilter is a @Component Filter, so @WebMvcTest instantiates it even with
    // addFilters = false (that flag only skips running filters, not building the bean); it needs
    // a JwtUtils bean to construct, which this slice otherwise wouldn't provide.
    @MockBean
    private JwtUtils jwtUtils;

    private UUID userID;

    @BeforeEach
    void setUp() {
        userID = UUID.randomUUID();
        when(accessTokenService.extractUserIDFromToken(AUTH_HEADER)).thenReturn(userID);
    }

    private static AddressDTO addressDTO() {
        return new AddressDTO(UUID.randomUUID(), "John Doe", "123 Main St", "Unit 1", "US", "12345", "555-1234", false);
    }

    private static PaymentMethodDTO paymentMethodDTO() {
        return new PaymentMethodDTO(UUID.randomUUID(), "VISA", "4242", "John Doe", "12", "2030", UUID.randomUUID(), false);
    }

    // ================= addresses =================

    @Test
    void getAllSavedAddresses_returnsAddressesForUser() throws Exception {
        List<AddressDTO> addresses = List.of(addressDTO());
        when(savedItemsService.getAllSavedAddressesForUser(userID)).thenReturn(addresses);

        mockMvc.perform(get("/junes/api/v1/saved-items/addresses/user").header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fullName").value("John Doe"));

        verify(savedItemsService).getAllSavedAddressesForUser(userID);
    }

    @Test
    void getSavedAddress_returnsAddressForUser() throws Exception {
        UUID savedItemID = UUID.randomUUID();
        AddressDTO address = addressDTO();
        when(savedItemsService.getSavedAddressForUser(savedItemID, userID)).thenReturn(address);

        mockMvc.perform(get("/junes/api/v1/saved-items/address/{savedItemID}", savedItemID).header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("John Doe"));

        verify(savedItemsService).getSavedAddressForUser(savedItemID, userID);
    }

    @Test
    void addAddress_delegatesToServiceAndReturnsSuccessMessage() throws Exception {
        AddressDTO addressDTO = addressDTO();

        mockMvc.perform(post("/junes/api/v1/saved-items/address")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addressDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Address successfully added"));

        verify(savedItemsService).addAddress(userID, addressDTO);
    }

    @Test
    void editAddress_delegatesToServiceAndReturnsSuccessMessage() throws Exception {
        UUID addressID = UUID.randomUUID();
        AddressDTO addressDTO = addressDTO();

        mockMvc.perform(put("/junes/api/v1/saved-items/address/{addressID}", addressID)
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addressDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Address successfully edited"));

        verify(savedItemsService).editAddress(userID, addressID, addressDTO);
    }

    @Test
    void deleteAddress_delegatesToServiceAndReturnsSuccessMessage() throws Exception {
        UUID addressID = UUID.randomUUID();

        mockMvc.perform(delete("/junes/api/v1/saved-items/address/{addressID}", addressID).header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Address successfully deleted"));

        verify(savedItemsService).deleteAddress(userID, addressID);
    }

    // ================= payment methods =================

    @Test
    void getAllSavedPaymentMethods_returnsPaymentMethodsForUser() throws Exception {
        List<PaymentMethodDTO> paymentMethods = List.of(paymentMethodDTO());
        when(savedItemsService.getAllPaymentMethodsForUser(userID)).thenReturn(paymentMethods);

        mockMvc.perform(get("/junes/api/v1/saved-items/payment-methods/user").header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cardLastFour").value("4242"));

        verify(savedItemsService).getAllPaymentMethodsForUser(userID);
    }

    @Test
    void getSavedPaymentMethod_returnsPaymentMethodForUser() throws Exception {
        UUID savedItemID = UUID.randomUUID();
        PaymentMethodDTO paymentMethod = paymentMethodDTO();
        when(savedItemsService.getSavedPaymentMethodForUser(savedItemID, userID)).thenReturn(paymentMethod);

        mockMvc.perform(get("/junes/api/v1/saved-items/payment-method/{savedItemID}", savedItemID).header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardLastFour").value("4242"));

        verify(savedItemsService).getSavedPaymentMethodForUser(savedItemID, userID);
    }

    @Test
    void addPaymentMethod_delegatesToServiceAndReturnsSuccessMessage() throws Exception {
        AddPaymentMethodRequest request = AddPaymentMethodRequest.builder().stripePaymentMethodID("pm_123").isDefault(true).build();
        String idempotencyKey = "idem-key-1";

        mockMvc.perform(post("/junes/api/v1/saved-items/payment-method")
                        .header("Authorization", AUTH_HEADER)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Payment method successfully added"));

        verify(savedItemsService).addPaymentMethod(userID, request, idempotencyKey);
    }

    @Test
    void editPaymentMethod_delegatesToServiceAndReturnsSuccessMessage() throws Exception {
        UUID paymentMethodID = UUID.randomUUID();
        EditPaymentMethodRequest request = new EditPaymentMethodRequest("John Doe", "01", "2031");
        String idempotencyKey = "idem-key-2";

        mockMvc.perform(put("/junes/api/v1/saved-items/payment-method/{paymentMethodID}", paymentMethodID)
                        .header("Authorization", AUTH_HEADER)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Payment method successfully edited"));

        verify(savedItemsService).editPaymentMethod(userID, paymentMethodID, request, idempotencyKey);
    }

    @Test
    void deletePaymentMethod_delegatesToServiceAndReturnsSuccessMessage() throws Exception {
        UUID paymentMethodID = UUID.randomUUID();
        String idempotencyKey = "idem-key-3";

        mockMvc.perform(delete("/junes/api/v1/saved-items/payment-method/{paymentMethodID}", paymentMethodID)
                        .header("Authorization", AUTH_HEADER)
                        .header("Idempotency-Key", idempotencyKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Payment method successfully deleted"));

        verify(savedItemsService).deletePaymentMethod(userID, paymentMethodID, idempotencyKey);
    }

    @Test
    void attachAddressToPaymentMethod_delegatesToServiceAndReturnsSuccessMessage() throws Exception {
        AttachAddressToPaymentMethodRequest request = new AttachAddressToPaymentMethodRequest(UUID.randomUUID(), UUID.randomUUID());
        String idempotencyKey = "idem-key-4";

        mockMvc.perform(post("/junes/api/v1/saved-items/attach")
                        .header("Authorization", AUTH_HEADER)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Address successfully attached to Payment method"));

        verify(savedItemsService).attachAddressToPaymentMethod(userID, request, idempotencyKey);
    }

    @Test
    void setAsDefault_delegatesToServiceAndReturnsSuccessMessage() throws Exception {
        SetAsDefaultRequest request = new SetAsDefaultRequest("ADDRESS", UUID.randomUUID());
        String idempotencyKey = "idem-key-5";

        mockMvc.perform(post("/junes/api/v1/saved-items/set-default")
                        .header("Authorization", AUTH_HEADER)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Saved item successfully set as default"));

        verify(savedItemsService).setAsDefault(userID, request.getMode(), request.getSavedItemID(), idempotencyKey);
    }

    @Test
    void createSetupIntent_returnsClientSecretFromStripeService() throws Exception {
        SetupIntentResponseDTO setupIntentResponse = new SetupIntentResponseDTO("client-secret-123");
        when(stripeService.createSetupIntent(userID)).thenReturn(setupIntentResponse);

        mockMvc.perform(post("/junes/api/v1/saved-items/payment-method/setup-intent").header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientSecret").value("client-secret-123"));

        verify(stripeService).createSetupIntent(userID);
    }
}
