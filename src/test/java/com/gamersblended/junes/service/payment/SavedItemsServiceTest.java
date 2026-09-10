package com.gamersblended.junes.service.payment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamersblended.junes.dto.AddressDTO;
import com.gamersblended.junes.dto.PaymentMethodDTO;
import com.gamersblended.junes.dto.request.AddPaymentMethodRequest;
import com.gamersblended.junes.dto.request.AttachAddressToPaymentMethodRequest;
import com.gamersblended.junes.dto.request.EditPaymentMethodRequest;
import com.gamersblended.junes.exception.*;
import com.gamersblended.junes.mapper.AddressMapper;
import com.gamersblended.junes.mapper.PaymentMethodMapper;
import com.gamersblended.junes.model.Address;
import com.gamersblended.junes.model.OutboxEvent;
import com.gamersblended.junes.model.PaymentMethod;
import com.gamersblended.junes.repository.jpa.AddressRepository;
import com.gamersblended.junes.repository.jpa.OutboxEventRepository;
import com.gamersblended.junes.repository.jpa.PaymentMethodRepository;
import com.gamersblended.junes.repository.jpa.UserRepository;
import com.gamersblended.junes.util.AddressValidator;
import com.gamersblended.junes.util.PaymentMethodValidator;
import com.stripe.StripeClient;
import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.StripeException;
import com.stripe.net.RequestOptions;
import com.stripe.service.PaymentMethodService;
import com.stripe.service.V1Services;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.gamersblended.junes.constant.KafkaConstants.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SavedItemsServiceTest {

    @Mock
    private AddressRepository addressRepository;
    @Mock
    private PaymentMethodRepository paymentMethodRepository;
    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AddressMapper addressMapper;
    @Mock
    private PaymentMethodMapper paymentMethodMapper;
    @Mock
    private AddressValidator addressValidator;
    @Mock
    private PaymentMethodValidator paymentMethodValidator;
    @Mock
    private StripeClient stripeClient;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private V1Services v1Services;
    @Mock
    private PaymentMethodService stripePaymentMethodService;

    private SavedItemsService savedItemsService;

    @BeforeEach
    void setUp() {
        savedItemsService = new SavedItemsService(addressRepository, addressMapper, paymentMethodRepository, paymentMethodMapper,
                addressValidator, paymentMethodValidator, userRepository, outboxEventRepository, objectMapper, stripeClient);
    }

    // ---- fixtures ----

    private static Address address(UUID id, UUID userID, boolean isDefault) {
        Address address = new Address();
        address.setAddressID(id);
        address.setUserID(userID);
        address.setIsDefault(isDefault);
        address.setFullName("John Doe");
        address.setAddressLine("1 Main St");
        address.setCountry("US");
        address.setZipCode("10001");
        address.setPhoneNumber("+12025550123");
        return address;
    }

    private static AddressDTO addressDTO(UUID id, boolean isDefault) {
        AddressDTO dto = new AddressDTO();
        dto.setAddressID(id);
        dto.setIsDefault(isDefault);
        dto.setFullName("John Doe");
        dto.setAddressLine("1 Main St");
        dto.setCountry("US");
        dto.setZipCode("10001");
        dto.setPhoneNumber("+12025550123");
        return dto;
    }

    private static PaymentMethod paymentMethod(UUID id, UUID userID, boolean isDefault) {
        PaymentMethod paymentMethod = new PaymentMethod();
        paymentMethod.setPaymentMethodID(id);
        paymentMethod.setUserID(userID);
        paymentMethod.setIsDefault(isDefault);
        paymentMethod.setIsActive(true);
        paymentMethod.setCardType("visa");
        paymentMethod.setCardLastFour("4242");
        paymentMethod.setCardHolderName("John Doe");
        paymentMethod.setExpirationMonth("05");
        paymentMethod.setExpirationYear("2030");
        paymentMethod.setCardFingerprint("fp_1");
        paymentMethod.setStripeCustomerID("cus_1");
        paymentMethod.setStripePaymentMethodID("pm_stripe_1");
        return paymentMethod;
    }

    private static com.stripe.model.PaymentMethod stripePaymentMethod(String fingerprint) {
        com.stripe.model.PaymentMethod.Card card = new com.stripe.model.PaymentMethod.Card();
        card.setBrand("visa");
        card.setLast4("4242");
        card.setFingerprint(fingerprint);
        card.setExpMonth(5L);
        card.setExpYear(2030L);

        com.stripe.model.PaymentMethod.BillingDetails billingDetails = new com.stripe.model.PaymentMethod.BillingDetails();
        billingDetails.setName("John Doe");

        com.stripe.model.PaymentMethod paymentMethod = new com.stripe.model.PaymentMethod();
        paymentMethod.setId("pm_new");
        paymentMethod.setCustomer("cus_1");
        paymentMethod.setCard(card);
        paymentMethod.setBillingDetails(billingDetails);
        return paymentMethod;
    }

    // ================= Addresses =================

    // ---- getAllSavedAddressesForUser ----

    @Test
    void getAllSavedAddressesForUser_returnsMappedAddresses() {
        UUID userID = UUID.randomUUID();
        Address address = address(UUID.randomUUID(), userID, false);
        AddressDTO dto = addressDTO(address.getAddressID(), false);
        when(addressRepository.getAddressesByUserID(userID)).thenReturn(List.of(address));
        when(addressMapper.toDTOList(List.of(address))).thenReturn(List.of(dto));

        List<AddressDTO> result = savedItemsService.getAllSavedAddressesForUser(userID);

        assertThat(result).containsExactly(dto);
    }

    // ---- getSavedAddressForUser ----

    @Test
    void getSavedAddressForUser_returnsMappedAddress_whenFound() {
        UUID userID = UUID.randomUUID();
        UUID addressID = UUID.randomUUID();
        Address address = address(addressID, userID, false);
        AddressDTO dto = addressDTO(addressID, false);
        when(addressRepository.getAddressByUserIDAndID(userID, addressID)).thenReturn(Optional.of(address));
        when(addressMapper.toDTO(address)).thenReturn(dto);

        AddressDTO result = savedItemsService.getSavedAddressForUser(addressID, userID);

        assertThat(result).isEqualTo(dto);
    }

    @Test
    void getSavedAddressForUser_throwsSavedItemNotFoundException_whenNotFound() {
        UUID userID = UUID.randomUUID();
        UUID addressID = UUID.randomUUID();
        when(addressRepository.getAddressByUserIDAndID(userID, addressID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> savedItemsService.getSavedAddressForUser(addressID, userID))
                .isInstanceOf(SavedItemNotFoundException.class);
    }

    // ---- addAddress ----

    @Test
    void addAddress_throwsSavedItemLimitExceededException_whenAtMaxAddresses() {
        UUID userID = UUID.randomUUID();
        AddressDTO dto = addressDTO(null, false);
        List<Address> fiveAddresses = List.of(
                address(UUID.randomUUID(), userID, false), address(UUID.randomUUID(), userID, false),
                address(UUID.randomUUID(), userID, false), address(UUID.randomUUID(), userID, false),
                address(UUID.randomUUID(), userID, false));
        when(addressRepository.getAddressesByUserID(userID)).thenReturn(fiveAddresses);

        assertThatThrownBy(() -> savedItemsService.addAddress(userID, dto))
                .isInstanceOf(SavedItemLimitExceededException.class);

        verify(addressValidator).validateAndSanitizeAddress(userID, dto);
        verify(addressRepository, never()).save(any());
    }

    @Test
    void addAddress_throwsDuplicateAddressException_whenDuplicateExists() {
        UUID userID = UUID.randomUUID();
        AddressDTO dto = addressDTO(null, false);
        Address existing = address(UUID.randomUUID(), userID, false);
        when(addressRepository.getAddressesByUserID(userID)).thenReturn(List.of(existing));
        when(addressValidator.isDuplicate(dto, existing)).thenReturn(true);

        assertThatThrownBy(() -> savedItemsService.addAddress(userID, dto))
                .isInstanceOf(DuplicateAddressException.class);

        verify(addressRepository, never()).save(any());
    }

    @Test
    void addAddress_unsetsPreviousDefault_whenNewAddressIsDefault() {
        UUID userID = UUID.randomUUID();
        AddressDTO dto = addressDTO(null, true);
        Address currentDefault = address(UUID.randomUUID(), userID, true);
        Address newAddress = new Address();
        when(addressRepository.getAddressesByUserID(userID)).thenReturn(List.of(currentDefault));
        when(addressValidator.isDuplicate(dto, currentDefault)).thenReturn(false);
        when(addressMapper.toEntity(dto)).thenReturn(newAddress);

        savedItemsService.addAddress(userID, dto);

        assertThat(currentDefault.getIsDefault()).isFalse();
        verify(addressRepository).save(currentDefault);
        assertThat(newAddress.getUserID()).isEqualTo(userID);
        verify(addressRepository).save(newAddress);
    }

    @Test
    void addAddress_savesNewAddress_whenNoDuplicatesAndUnderLimit() {
        UUID userID = UUID.randomUUID();
        AddressDTO dto = addressDTO(null, false);
        Address newAddress = new Address();
        when(addressRepository.getAddressesByUserID(userID)).thenReturn(List.of());
        when(addressMapper.toEntity(dto)).thenReturn(newAddress);

        savedItemsService.addAddress(userID, dto);

        assertThat(newAddress.getUserID()).isEqualTo(userID);
        verify(addressRepository).save(newAddress);
    }

    // ---- editAddress ----

    @Test
    void editAddress_throwsInputValidationException_whenTargetAddressIDIsNull() {
        UUID userID = UUID.randomUUID();
        AddressDTO dto = addressDTO(null, false);

        assertThatThrownBy(() -> savedItemsService.editAddress(userID, null, dto))
                .isInstanceOf(InputValidationException.class);

        verifyNoInteractions(addressValidator, addressRepository);
    }

    @Test
    void editAddress_throwsSavedItemNotFoundException_whenAddressNotInUserList() {
        UUID userID = UUID.randomUUID();
        UUID targetID = UUID.randomUUID();
        AddressDTO dto = addressDTO(targetID, false);
        when(addressRepository.getAddressesByUserID(userID)).thenReturn(List.of());

        assertThatThrownBy(() -> savedItemsService.editAddress(userID, targetID, dto))
                .isInstanceOf(SavedItemNotFoundException.class);

        verify(addressRepository, never()).save(any());
    }

    @Test
    void editAddress_updatesAndSaves_onSuccess() {
        UUID userID = UUID.randomUUID();
        UUID targetID = UUID.randomUUID();
        Address addressToUpdate = address(targetID, userID, false);
        AddressDTO dto = addressDTO(targetID, false);
        when(addressRepository.getAddressesByUserID(userID)).thenReturn(List.of(addressToUpdate));

        savedItemsService.editAddress(userID, targetID, dto);

        verify(addressMapper).updateEntityFromDTO(dto, addressToUpdate);
        verify(addressRepository).save(addressToUpdate);
    }

    // ---- deleteAddress ----

    @Test
    void deleteAddress_throwsInputValidationException_whenTargetAddressIDIsNull() {
        UUID userID = UUID.randomUUID();

        assertThatThrownBy(() -> savedItemsService.deleteAddress(userID, null))
                .isInstanceOf(InputValidationException.class);

        verifyNoInteractions(addressRepository);
    }

    @Test
    void deleteAddress_throwsSavedItemNotFoundException_whenNotFound() {
        UUID userID = UUID.randomUUID();
        UUID targetID = UUID.randomUUID();
        when(addressRepository.getAddressByUserIDAndID(userID, targetID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> savedItemsService.deleteAddress(userID, targetID))
                .isInstanceOf(SavedItemNotFoundException.class);
    }

    @Test
    void deleteAddress_softDeletesAddress_onSuccess() {
        UUID userID = UUID.randomUUID();
        UUID targetID = UUID.randomUUID();
        Address address = address(targetID, userID, false);
        when(addressRepository.getAddressByUserIDAndID(userID, targetID)).thenReturn(Optional.of(address));

        savedItemsService.deleteAddress(userID, targetID);

        assertThat(address.getDeletedOn()).isNotNull();
        verify(addressRepository).save(address);
    }

    // ================= Payment Methods =================

    // ---- getAllPaymentMethodsForUser ----

    @Test
    void getAllPaymentMethodsForUser_returnsMappedPaymentMethods() {
        UUID userID = UUID.randomUUID();
        PaymentMethod paymentMethod = paymentMethod(UUID.randomUUID(), userID, false);
        PaymentMethodDTO dto = new PaymentMethodDTO();
        when(paymentMethodRepository.getPaymentMethodsByUserID(userID)).thenReturn(List.of(paymentMethod));
        when(paymentMethodMapper.toDTOList(List.of(paymentMethod))).thenReturn(List.of(dto));

        List<PaymentMethodDTO> result = savedItemsService.getAllPaymentMethodsForUser(userID);

        assertThat(result).containsExactly(dto);
    }

    // ---- getSavedPaymentMethodForUser ----

    @Test
    void getSavedPaymentMethodForUser_returnsMappedPaymentMethod_whenFound() {
        UUID userID = UUID.randomUUID();
        UUID paymentMethodID = UUID.randomUUID();
        PaymentMethod paymentMethod = paymentMethod(paymentMethodID, userID, false);
        PaymentMethodDTO dto = new PaymentMethodDTO();
        when(paymentMethodRepository.getPaymentMethodByUserIDAndID(userID, paymentMethodID)).thenReturn(Optional.of(paymentMethod));
        when(paymentMethodMapper.toDTO(paymentMethod)).thenReturn(dto);

        PaymentMethodDTO result = savedItemsService.getSavedPaymentMethodForUser(paymentMethodID, userID);

        assertThat(result).isEqualTo(dto);
    }

    @Test
    void getSavedPaymentMethodForUser_throwsSavedItemNotFoundException_whenNotFound() {
        UUID userID = UUID.randomUUID();
        UUID paymentMethodID = UUID.randomUUID();
        when(paymentMethodRepository.getPaymentMethodByUserIDAndID(userID, paymentMethodID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> savedItemsService.getSavedPaymentMethodForUser(paymentMethodID, userID))
                .isInstanceOf(SavedItemNotFoundException.class);
    }

    // ---- addPaymentMethod ----

    private void stubStripeRetrieve(com.stripe.model.PaymentMethod toReturn) throws StripeException {
        when(stripeClient.v1()).thenReturn(v1Services);
        when(v1Services.paymentMethods()).thenReturn(stripePaymentMethodService);
        when(stripePaymentMethodService.retrieve(anyString(), any(RequestOptions.class))).thenReturn(toReturn);
    }

    @Test
    void addPaymentMethod_throwsSavedItemLimitExceededException_whenAtMaxPaymentMethods() {
        UUID userID = UUID.randomUUID();
        AddPaymentMethodRequest request = AddPaymentMethodRequest.builder().stripePaymentMethodID("pm_new").isDefault(false).build();
        List<PaymentMethod> fivePaymentMethods = List.of(
                paymentMethod(UUID.randomUUID(), userID, false), paymentMethod(UUID.randomUUID(), userID, false),
                paymentMethod(UUID.randomUUID(), userID, false), paymentMethod(UUID.randomUUID(), userID, false),
                paymentMethod(UUID.randomUUID(), userID, false));
        when(paymentMethodRepository.getPaymentMethodsByUserID(userID)).thenReturn(fivePaymentMethods);

        assertThatThrownBy(() -> savedItemsService.addPaymentMethod(userID, request, "idem-1"))
                .isInstanceOf(SavedItemLimitExceededException.class);

        verifyNoInteractions(stripeClient);
    }

    @Test
    void addPaymentMethod_throwsStripeOperationException_whenStripeCustomerIDNotFound() throws StripeException {
        UUID userID = UUID.randomUUID();
        AddPaymentMethodRequest request = AddPaymentMethodRequest.builder().stripePaymentMethodID("pm_new").isDefault(false).build();
        when(paymentMethodRepository.getPaymentMethodsByUserID(userID)).thenReturn(List.of());
        stubStripeRetrieve(stripePaymentMethod("fp_new"));
        when(userRepository.getStripeCustomerID(userID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> savedItemsService.addPaymentMethod(userID, request, "idem-1"))
                .isInstanceOf(StripeOperationException.class);

        verify(paymentMethodRepository, never()).save(any());
    }

    @Test
    void addPaymentMethod_propagatesStripeException_whenRetrieveFails() throws StripeException {
        UUID userID = UUID.randomUUID();
        AddPaymentMethodRequest request = AddPaymentMethodRequest.builder().stripePaymentMethodID("pm_new").isDefault(false).build();
        when(paymentMethodRepository.getPaymentMethodsByUserID(userID)).thenReturn(List.of());
        when(stripeClient.v1()).thenReturn(v1Services);
        when(v1Services.paymentMethods()).thenReturn(stripePaymentMethodService);
        when(stripePaymentMethodService.retrieve(anyString(), any(RequestOptions.class)))
                .thenThrow(new ApiConnectionException("stripe down"));

        assertThatThrownBy(() -> savedItemsService.addPaymentMethod(userID, request, "idem-1"))
                .isInstanceOf(ApiConnectionException.class);
    }

    @Test
    void addPaymentMethod_savesNewPaymentMethod_whenValidAndNotDuplicateAndNotDefault() throws Exception {
        UUID userID = UUID.randomUUID();
        AddPaymentMethodRequest request = AddPaymentMethodRequest.builder().stripePaymentMethodID("pm_new").isDefault(false).build();
        when(paymentMethodRepository.getPaymentMethodsByUserID(userID)).thenReturn(List.of());
        stubStripeRetrieve(stripePaymentMethod("fp_new"));
        when(userRepository.getStripeCustomerID(userID)).thenReturn(Optional.of("cus_1"));
        when(paymentMethodRepository.findByUserIDAndCardFingerprintAndIsActiveTrue(userID, "fp_new")).thenReturn(Optional.empty());

        savedItemsService.addPaymentMethod(userID, request, "idem-1");

        verify(paymentMethodValidator).validatePaymentMethodForAdd(eq(userID), eq("cus_1"), any());
        ArgumentCaptor<PaymentMethod> captor = ArgumentCaptor.forClass(PaymentMethod.class);
        verify(paymentMethodRepository).save(captor.capture());
        PaymentMethod saved = captor.getValue();
        assertThat(saved.getCardType()).isEqualTo("visa");
        assertThat(saved.getCardLastFour()).isEqualTo("4242");
        assertThat(saved.getCardHolderName()).isEqualTo("John Doe");
        assertThat(saved.getExpirationMonth()).isEqualTo("5");
        assertThat(saved.getExpirationYear()).isEqualTo("2030");
        assertThat(saved.getUserID()).isEqualTo(userID);
        assertThat(saved.getIsActive()).isTrue();
        assertThat(saved.getIsDefault()).isFalse();
        assertThat(saved.getCardFingerprint()).isEqualTo("fp_new");
        assertThat(saved.getStripeCustomerID()).isEqualTo("cus_1");
        assertThat(saved.getStripePaymentMethodID()).isEqualTo("pm_new");

        verify(paymentMethodRepository, never()).unsetDefaultForUser(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void addPaymentMethod_throwsDuplicatePaymentMethodException_andQueuesOrphanDetach_whenDuplicateCardFound() throws Exception {
        UUID userID = UUID.randomUUID();
        AddPaymentMethodRequest request = AddPaymentMethodRequest.builder().stripePaymentMethodID("pm_new").isDefault(false).build();
        PaymentMethod existingPM = paymentMethod(UUID.randomUUID(), userID, false);
        when(paymentMethodRepository.getPaymentMethodsByUserID(userID)).thenReturn(List.of());
        stubStripeRetrieve(stripePaymentMethod("fp_1"));
        when(userRepository.getStripeCustomerID(userID)).thenReturn(Optional.of("cus_1"));
        when(paymentMethodRepository.findByUserIDAndCardFingerprintAndIsActiveTrue(userID, "fp_1")).thenReturn(Optional.of(existingPM));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        assertThatThrownBy(() -> savedItemsService.addPaymentMethod(userID, request, "idem-1"))
                .isInstanceOf(DuplicatePaymentMethodException.class);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(PAYMENT_METHOD_DETACHED);
        assertThat(captor.getValue().getTopic()).isEqualTo(STRIPE_DETACH_PM_EVENTS);
        assertThat(captor.getValue().getIdempotencyKey()).isEqualTo("idem-1-orphan-detach");
        verify(paymentMethodRepository, never()).save(any());
    }

    @Test
    void addPaymentMethod_throwsDuplicatePaymentMethodException_butSkipsOrphanDetach_whenAlreadyQueued() throws Exception {
        UUID userID = UUID.randomUUID();
        AddPaymentMethodRequest request = AddPaymentMethodRequest.builder().stripePaymentMethodID("pm_new").isDefault(false).build();
        PaymentMethod existingPM = paymentMethod(UUID.randomUUID(), userID, false);
        when(paymentMethodRepository.getPaymentMethodsByUserID(userID)).thenReturn(List.of());
        stubStripeRetrieve(stripePaymentMethod("fp_1"));
        when(userRepository.getStripeCustomerID(userID)).thenReturn(Optional.of("cus_1"));
        when(paymentMethodRepository.findByUserIDAndCardFingerprintAndIsActiveTrue(userID, "fp_1")).thenReturn(Optional.of(existingPM));
        when(outboxEventRepository.existsByAggregateIDAndEventTypeAndIdempotencyKey(
                String.valueOf(userID), PAYMENT_METHOD_DETACHED, "idem-1-orphan-detach")).thenReturn(true);

        assertThatThrownBy(() -> savedItemsService.addPaymentMethod(userID, request, "idem-1"))
                .isInstanceOf(DuplicatePaymentMethodException.class);

        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void addPaymentMethod_unsetsOldDefault_andQueuesSetDefaultEvent_whenIsDefaultTrue() throws Exception {
        UUID userID = UUID.randomUUID();
        AddPaymentMethodRequest request = AddPaymentMethodRequest.builder().stripePaymentMethodID("pm_new").isDefault(true).build();
        when(paymentMethodRepository.getPaymentMethodsByUserID(userID)).thenReturn(List.of());
        stubStripeRetrieve(stripePaymentMethod("fp_new"));
        when(userRepository.getStripeCustomerID(userID)).thenReturn(Optional.of("cus_1"));
        when(paymentMethodRepository.findByUserIDAndCardFingerprintAndIsActiveTrue(userID, "fp_new")).thenReturn(Optional.empty());
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        savedItemsService.addPaymentMethod(userID, request, "idem-1");

        verify(paymentMethodRepository).unsetDefaultForUser(userID);
        ArgumentCaptor<PaymentMethod> pmCaptor = ArgumentCaptor.forClass(PaymentMethod.class);
        verify(paymentMethodRepository).save(pmCaptor.capture());
        assertThat(pmCaptor.getValue().getIsDefault()).isTrue();

        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo(PAYMENT_METHOD_SET_DEFAULT);
        assertThat(eventCaptor.getValue().getTopic()).isEqualTo(STRIPE_PM_SYNC_EVENTS);
    }

    // ---- editPaymentMethod ----

    @Test
    void editPaymentMethod_throwsInputValidationException_whenTargetIDIsNull() {
        UUID userID = UUID.randomUUID();
        EditPaymentMethodRequest request = new EditPaymentMethodRequest("John Doe", "05", "2030");

        assertThatThrownBy(() -> savedItemsService.editPaymentMethod(userID, null, request, "idem-1"))
                .isInstanceOf(InputValidationException.class);

        verifyNoInteractions(paymentMethodValidator, paymentMethodRepository);
    }

    @Test
    void editPaymentMethod_throwsIllegalArgumentException_whenNotFound() {
        UUID userID = UUID.randomUUID();
        UUID targetID = UUID.randomUUID();
        EditPaymentMethodRequest request = new EditPaymentMethodRequest("John Doe", "05", "2030");
        when(paymentMethodRepository.getPaymentMethodByUserIDAndID(userID, targetID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> savedItemsService.editPaymentMethod(userID, targetID, request, "idem-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void editPaymentMethod_skipsUpdate_whenNoChangesDetected() {
        UUID userID = UUID.randomUUID();
        UUID targetID = UUID.randomUUID();
        PaymentMethod current = paymentMethod(targetID, userID, false);
        EditPaymentMethodRequest request = new EditPaymentMethodRequest("John Doe", "5", "2030");
        when(paymentMethodRepository.getPaymentMethodByUserIDAndID(userID, targetID)).thenReturn(Optional.of(current));

        savedItemsService.editPaymentMethod(userID, targetID, request, "idem-1");

        verify(paymentMethodRepository, never()).save(any());
        verifyNoInteractions(outboxEventRepository);
    }

    @Test
    void editPaymentMethod_skipsUpdate_whenDuplicateSubmission() {
        UUID userID = UUID.randomUUID();
        UUID targetID = UUID.randomUUID();
        PaymentMethod current = paymentMethod(targetID, userID, false);
        EditPaymentMethodRequest request = new EditPaymentMethodRequest("Jane Doe", "05", "2030");
        when(paymentMethodRepository.getPaymentMethodByUserIDAndID(userID, targetID)).thenReturn(Optional.of(current));
        when(outboxEventRepository.existsByAggregateIDAndEventTypeAndIdempotencyKey(
                String.valueOf(userID), PAYMENT_METHOD_EDITED, "idem-1")).thenReturn(true);

        savedItemsService.editPaymentMethod(userID, targetID, request, "idem-1");

        verify(paymentMethodRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void editPaymentMethod_updatesAndQueuesStripeSyncEvent_whenChanged() throws Exception {
        UUID userID = UUID.randomUUID();
        UUID targetID = UUID.randomUUID();
        PaymentMethod current = paymentMethod(targetID, userID, false);
        EditPaymentMethodRequest request = new EditPaymentMethodRequest("Jane Doe", "06", "2031");
        when(paymentMethodRepository.getPaymentMethodByUserIDAndID(userID, targetID)).thenReturn(Optional.of(current));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        savedItemsService.editPaymentMethod(userID, targetID, request, "idem-1");

        assertThat(current.getCardHolderName()).isEqualTo("Jane Doe");
        assertThat(current.getExpirationMonth()).isEqualTo("06");
        assertThat(current.getExpirationYear()).isEqualTo("2031");
        verify(paymentMethodRepository).save(current);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(PAYMENT_METHOD_EDITED);
        assertThat(captor.getValue().getTopic()).isEqualTo(STRIPE_PM_SYNC_EVENTS);
        assertThat(captor.getValue().getIdempotencyKey()).isEqualTo("idem-1");
    }

    @Test
    void editPaymentMethod_throwsOutboxEventCreationException_whenSerializationFails() throws Exception {
        UUID userID = UUID.randomUUID();
        UUID targetID = UUID.randomUUID();
        PaymentMethod current = paymentMethod(targetID, userID, false);
        EditPaymentMethodRequest request = new EditPaymentMethodRequest("Jane Doe", "06", "2031");
        when(paymentMethodRepository.getPaymentMethodByUserIDAndID(userID, targetID)).thenReturn(Optional.of(current));
        when(objectMapper.writeValueAsString(any())).thenThrow(mock(JsonProcessingException.class));

        assertThatThrownBy(() -> savedItemsService.editPaymentMethod(userID, targetID, request, "idem-1"))
                .isInstanceOf(OutboxEventCreationException.class);
    }

    // ---- deletePaymentMethod ----

    @Test
    void deletePaymentMethod_throwsInputValidationException_whenTargetIDIsNull() {
        UUID userID = UUID.randomUUID();

        assertThatThrownBy(() -> savedItemsService.deletePaymentMethod(userID, null, "idem-1"))
                .isInstanceOf(InputValidationException.class);

        verifyNoInteractions(paymentMethodRepository);
    }

    @Test
    void deletePaymentMethod_throwsSavedItemNotFoundException_whenNotFound() {
        UUID userID = UUID.randomUUID();
        UUID targetID = UUID.randomUUID();
        when(paymentMethodRepository.getPaymentMethodByUserIDAndID(userID, targetID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> savedItemsService.deletePaymentMethod(userID, targetID, "idem-1"))
                .isInstanceOf(SavedItemNotFoundException.class);
    }

    @Test
    void deletePaymentMethod_skipsDelete_whenDuplicateSubmission() {
        UUID userID = UUID.randomUUID();
        UUID targetID = UUID.randomUUID();
        PaymentMethod paymentMethod = paymentMethod(targetID, userID, false);
        when(paymentMethodRepository.getPaymentMethodByUserIDAndID(userID, targetID)).thenReturn(Optional.of(paymentMethod));
        when(outboxEventRepository.existsByAggregateIDAndEventTypeAndIdempotencyKey(
                String.valueOf(userID), PAYMENT_METHOD_DETACHED, "idem-1")).thenReturn(true);

        savedItemsService.deletePaymentMethod(userID, targetID, "idem-1");

        assertThat(paymentMethod.getIsActive()).isTrue();
        verify(paymentMethodRepository, never()).save(any());
    }

    @Test
    void deletePaymentMethod_softDeletesAndQueuesDetachEvent_onSuccess() throws Exception {
        UUID userID = UUID.randomUUID();
        UUID targetID = UUID.randomUUID();
        PaymentMethod paymentMethod = paymentMethod(targetID, userID, false);
        when(paymentMethodRepository.getPaymentMethodByUserIDAndID(userID, targetID)).thenReturn(Optional.of(paymentMethod));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        savedItemsService.deletePaymentMethod(userID, targetID, "idem-1");

        assertThat(paymentMethod.getIsActive()).isFalse();
        verify(paymentMethodRepository).save(paymentMethod);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(PAYMENT_METHOD_DETACHED);
        assertThat(captor.getValue().getTopic()).isEqualTo(STRIPE_DETACH_PM_EVENTS);
    }

    // ---- attachAddressToPaymentMethod ----

    @Test
    void attachAddressToPaymentMethod_throwsInputValidationException_whenAddressIDIsNull() {
        UUID userID = UUID.randomUUID();
        AttachAddressToPaymentMethodRequest request = new AttachAddressToPaymentMethodRequest(null, UUID.randomUUID());

        assertThatThrownBy(() -> savedItemsService.attachAddressToPaymentMethod(userID, request, "idem-1"))
                .isInstanceOf(InputValidationException.class);
    }

    @Test
    void attachAddressToPaymentMethod_throwsSavedItemNotFoundException_whenAddressNotFound() {
        UUID userID = UUID.randomUUID();
        UUID addressID = UUID.randomUUID();
        AttachAddressToPaymentMethodRequest request = new AttachAddressToPaymentMethodRequest(addressID, UUID.randomUUID());
        when(addressRepository.getAddressByUserIDAndID(userID, addressID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> savedItemsService.attachAddressToPaymentMethod(userID, request, "idem-1"))
                .isInstanceOf(SavedItemNotFoundException.class);
    }

    @Test
    void attachAddressToPaymentMethod_throwsInputValidationException_whenPaymentMethodIDIsNull() {
        UUID userID = UUID.randomUUID();
        UUID addressID = UUID.randomUUID();
        Address address = address(addressID, userID, false);
        AttachAddressToPaymentMethodRequest request = new AttachAddressToPaymentMethodRequest(addressID, null);
        when(addressRepository.getAddressByUserIDAndID(userID, addressID)).thenReturn(Optional.of(address));

        assertThatThrownBy(() -> savedItemsService.attachAddressToPaymentMethod(userID, request, "idem-1"))
                .isInstanceOf(InputValidationException.class);
    }

    @Test
    void attachAddressToPaymentMethod_throwsSavedItemNotFoundException_whenPaymentMethodNotFound() {
        UUID userID = UUID.randomUUID();
        UUID addressID = UUID.randomUUID();
        UUID paymentMethodID = UUID.randomUUID();
        Address address = address(addressID, userID, false);
        AttachAddressToPaymentMethodRequest request = new AttachAddressToPaymentMethodRequest(addressID, paymentMethodID);
        when(addressRepository.getAddressByUserIDAndID(userID, addressID)).thenReturn(Optional.of(address));
        when(paymentMethodRepository.getPaymentMethodByUserIDAndID(userID, paymentMethodID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> savedItemsService.attachAddressToPaymentMethod(userID, request, "idem-1"))
                .isInstanceOf(SavedItemNotFoundException.class);
    }

    @Test
    void attachAddressToPaymentMethod_skipsUpdate_whenAlreadySetToTargetAddress() {
        UUID userID = UUID.randomUUID();
        UUID addressID = UUID.randomUUID();
        UUID paymentMethodID = UUID.randomUUID();
        Address address = address(addressID, userID, false);
        PaymentMethod paymentMethod = paymentMethod(paymentMethodID, userID, false);
        paymentMethod.setBillingAddressID(addressID);
        AttachAddressToPaymentMethodRequest request = new AttachAddressToPaymentMethodRequest(addressID, paymentMethodID);
        when(addressRepository.getAddressByUserIDAndID(userID, addressID)).thenReturn(Optional.of(address));
        when(paymentMethodRepository.getPaymentMethodByUserIDAndID(userID, paymentMethodID)).thenReturn(Optional.of(paymentMethod));

        savedItemsService.attachAddressToPaymentMethod(userID, request, "idem-1");

        verify(paymentMethodRepository, never()).save(any());
    }

    @Test
    void attachAddressToPaymentMethod_skipsUpdate_whenDuplicateSubmission() {
        UUID userID = UUID.randomUUID();
        UUID addressID = UUID.randomUUID();
        UUID paymentMethodID = UUID.randomUUID();
        Address address = address(addressID, userID, false);
        PaymentMethod paymentMethod = paymentMethod(paymentMethodID, userID, false);
        AttachAddressToPaymentMethodRequest request = new AttachAddressToPaymentMethodRequest(addressID, paymentMethodID);
        when(addressRepository.getAddressByUserIDAndID(userID, addressID)).thenReturn(Optional.of(address));
        when(paymentMethodRepository.getPaymentMethodByUserIDAndID(userID, paymentMethodID)).thenReturn(Optional.of(paymentMethod));
        when(outboxEventRepository.existsByAggregateIDAndEventTypeAndIdempotencyKey(
                String.valueOf(userID), PAYMENT_METHOD_ADDRESS_ATTACHED, "idem-1")).thenReturn(true);

        savedItemsService.attachAddressToPaymentMethod(userID, request, "idem-1");

        verify(paymentMethodRepository, never()).save(any());
    }

    @Test
    void attachAddressToPaymentMethod_attachesAddress_andQueuesStripeSyncEvent_onSuccess() throws Exception {
        UUID userID = UUID.randomUUID();
        UUID addressID = UUID.randomUUID();
        UUID paymentMethodID = UUID.randomUUID();
        Address address = address(addressID, userID, false);
        PaymentMethod paymentMethod = paymentMethod(paymentMethodID, userID, false);
        AttachAddressToPaymentMethodRequest request = new AttachAddressToPaymentMethodRequest(addressID, paymentMethodID);
        when(addressRepository.getAddressByUserIDAndID(userID, addressID)).thenReturn(Optional.of(address));
        when(paymentMethodRepository.getPaymentMethodByUserIDAndID(userID, paymentMethodID)).thenReturn(Optional.of(paymentMethod));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        savedItemsService.attachAddressToPaymentMethod(userID, request, "idem-1");

        assertThat(paymentMethod.getBillingAddressID()).isEqualTo(addressID);
        verify(paymentMethodRepository).save(paymentMethod);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(PAYMENT_METHOD_ADDRESS_ATTACHED);
        assertThat(captor.getValue().getTopic()).isEqualTo(STRIPE_PM_SYNC_EVENTS);
    }

    // ---- setAsDefault ----

    @Test
    void setAsDefault_throwsInputValidationException_whenSavedItemIDIsNull() {
        UUID userID = UUID.randomUUID();

        assertThatThrownBy(() -> savedItemsService.setAsDefault(userID, "address", null, "idem-1"))
                .isInstanceOf(InputValidationException.class);

        verifyNoInteractions(addressRepository, paymentMethodRepository);
    }

    @Test
    void setAsDefault_doesNothing_whenModeIsUnrecognized() {
        UUID userID = UUID.randomUUID();
        UUID savedItemID = UUID.randomUUID();

        savedItemsService.setAsDefault(userID, "unknown_mode", savedItemID, "idem-1");

        verifyNoInteractions(addressRepository, paymentMethodRepository, outboxEventRepository);
    }

    @Test
    void setAsDefault_address_throwsSavedItemNotFoundException_whenNotFound() {
        UUID userID = UUID.randomUUID();
        UUID addressID = UUID.randomUUID();
        when(addressRepository.getAddressByUserIDAndID(userID, addressID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> savedItemsService.setAsDefault(userID, "address", addressID, "idem-1"))
                .isInstanceOf(SavedItemNotFoundException.class);
    }

    @Test
    void setAsDefault_address_skipsUpdate_whenAlreadyDefault() {
        UUID userID = UUID.randomUUID();
        UUID addressID = UUID.randomUUID();
        Address address = address(addressID, userID, true);
        when(addressRepository.getAddressByUserIDAndID(userID, addressID)).thenReturn(Optional.of(address));

        savedItemsService.setAsDefault(userID, "address", addressID, "idem-1");

        verify(addressRepository, never()).unsetDefaultForUser(any());
        verify(addressRepository, never()).save(any());
    }

    @Test
    void setAsDefault_address_updatesDefault_onSuccess() {
        UUID userID = UUID.randomUUID();
        UUID addressID = UUID.randomUUID();
        Address address = address(addressID, userID, false);
        when(addressRepository.getAddressByUserIDAndID(userID, addressID)).thenReturn(Optional.of(address));

        savedItemsService.setAsDefault(userID, "address", addressID, "idem-1");

        verify(addressRepository).unsetDefaultForUser(userID);
        assertThat(address.getIsDefault()).isTrue();
        verify(addressRepository).save(address);
    }

    @Test
    void setAsDefault_paymentMethod_throwsSavedItemNotFoundException_whenNotFound() {
        UUID userID = UUID.randomUUID();
        UUID paymentMethodID = UUID.randomUUID();
        when(paymentMethodRepository.getPaymentMethodByUserIDAndID(userID, paymentMethodID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> savedItemsService.setAsDefault(userID, "payment_method", paymentMethodID, "idem-1"))
                .isInstanceOf(SavedItemNotFoundException.class);
    }

    @Test
    void setAsDefault_paymentMethod_skipsUpdate_whenAlreadyDefault() {
        UUID userID = UUID.randomUUID();
        UUID paymentMethodID = UUID.randomUUID();
        PaymentMethod paymentMethod = paymentMethod(paymentMethodID, userID, true);
        when(paymentMethodRepository.getPaymentMethodByUserIDAndID(userID, paymentMethodID)).thenReturn(Optional.of(paymentMethod));

        savedItemsService.setAsDefault(userID, "payment_method", paymentMethodID, "idem-1");

        verify(paymentMethodRepository, never()).unsetDefaultForUser(any());
        verify(paymentMethodRepository, never()).save(any());
    }

    @Test
    void setAsDefault_paymentMethod_skipsUpdate_whenDuplicateSubmission() {
        UUID userID = UUID.randomUUID();
        UUID paymentMethodID = UUID.randomUUID();
        PaymentMethod paymentMethod = paymentMethod(paymentMethodID, userID, false);
        when(paymentMethodRepository.getPaymentMethodByUserIDAndID(userID, paymentMethodID)).thenReturn(Optional.of(paymentMethod));
        when(outboxEventRepository.existsByAggregateIDAndEventTypeAndIdempotencyKey(
                String.valueOf(userID), PAYMENT_METHOD_SET_DEFAULT, "idem-1")).thenReturn(true);

        savedItemsService.setAsDefault(userID, "payment_method", paymentMethodID, "idem-1");

        verify(paymentMethodRepository, never()).unsetDefaultForUser(any());
        verify(paymentMethodRepository, never()).save(any());
    }

    @Test
    void setAsDefault_paymentMethod_setsDefault_andQueuesStripeSyncEvent_onSuccess() throws Exception {
        UUID userID = UUID.randomUUID();
        UUID paymentMethodID = UUID.randomUUID();
        PaymentMethod paymentMethod = paymentMethod(paymentMethodID, userID, false);
        when(paymentMethodRepository.getPaymentMethodByUserIDAndID(userID, paymentMethodID)).thenReturn(Optional.of(paymentMethod));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        savedItemsService.setAsDefault(userID, "payment_method", paymentMethodID, "idem-1");

        verify(paymentMethodRepository).unsetDefaultForUser(userID);
        assertThat(paymentMethod.getIsDefault()).isTrue();
        verify(paymentMethodRepository).save(paymentMethod);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(PAYMENT_METHOD_SET_DEFAULT);
        assertThat(captor.getValue().getTopic()).isEqualTo(STRIPE_PM_SYNC_EVENTS);
    }
}
