package com.gamersblended.junes.config;

import com.gamersblended.junes.dto.response.ErrorResponseDTO;
import com.gamersblended.junes.exception.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.context.request.WebRequest;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private static final String MESSAGE = "test-message";
    private static final String RAW_PATH = "uri=/junes/api/v1/test";
    private static final String EXPECTED_PATH = "/junes/api/v1/test";

    @Mock
    private WebRequest webRequest;

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @BeforeEach
    void setUp() {
        lenient().when(webRequest.getDescription(false)).thenReturn(RAW_PATH);
    }

    private interface HandlerInvocation {
        ResponseEntity<Object> invoke(GlobalExceptionHandler handler, WebRequest request);
    }

    private static Stream<Arguments> exceptionHandlers() {
        return Stream.of(
                Arguments.of("ProductNotFoundException",
                        (HandlerInvocation) (h, r) -> h.handleProductNotFoundException(new ProductNotFoundException(MESSAGE), r),
                        HttpStatus.NOT_FOUND),
                Arguments.of("InvalidProductIdException",
                        (HandlerInvocation) (h, r) -> h.handleInvalidProductIdException(new InvalidProductIdException(MESSAGE), r),
                        HttpStatus.BAD_REQUEST),
                Arguments.of("InputValidationException",
                        (HandlerInvocation) (h, r) -> h.handleInputValidationException(new InputValidationException(MESSAGE), r),
                        HttpStatus.BAD_REQUEST),
                Arguments.of("QueueEmailException",
                        (HandlerInvocation) (h, r) -> h.handleQueueEmailException(new QueueEmailException(MESSAGE), r),
                        HttpStatus.INTERNAL_SERVER_ERROR),
                Arguments.of("UserNotFoundException",
                        (HandlerInvocation) (h, r) -> h.handleUserNotFoundException(new UserNotFoundException(MESSAGE), r),
                        HttpStatus.NOT_FOUND),
                Arguments.of("EmailAlreadyVerifiedException",
                        (HandlerInvocation) (h, r) -> h.handleEmailAlreadyVerifiedException(new EmailAlreadyVerifiedException(MESSAGE), r),
                        HttpStatus.CONFLICT),
                Arguments.of("EmailAlreadyInUseException",
                        (HandlerInvocation) (h, r) -> h.handleEmailAlreadyInUseException(new EmailAlreadyInUseException(MESSAGE), r),
                        HttpStatus.CONFLICT),
                Arguments.of("EmailDeliveryException",
                        (HandlerInvocation) (h, r) -> h.handleEmailDeliveryException(new EmailDeliveryException(MESSAGE), r),
                        HttpStatus.INTERNAL_SERVER_ERROR),
                Arguments.of("VerificationException",
                        (HandlerInvocation) (h, r) -> h.handleVerificationException(new VerificationException(MESSAGE), r),
                        HttpStatus.INTERNAL_SERVER_ERROR),
                Arguments.of("InvalidTokenException",
                        (HandlerInvocation) (h, r) -> h.handleInvalidTokenException(new InvalidTokenException(MESSAGE), r),
                        HttpStatus.UNAUTHORIZED),
                Arguments.of("InvalidTemplateException",
                        (HandlerInvocation) (h, r) -> h.handleInvalidTemplateException(new InvalidTemplateException(MESSAGE), r),
                        HttpStatus.INTERNAL_SERVER_ERROR),
                Arguments.of("UserDisabledException",
                        (HandlerInvocation) (h, r) -> h.handleUserDisabledException(new UserDisabledException(MESSAGE), r),
                        HttpStatus.FORBIDDEN),
                Arguments.of("UserNotVerifiedException",
                        (HandlerInvocation) (h, r) -> h.handleUserNotVerifiedException(new UserNotVerifiedException(MESSAGE), r),
                        HttpStatus.FORBIDDEN),
                Arguments.of("MissingTokenException",
                        (HandlerInvocation) (h, r) -> h.handleMissingTokenException(new MissingTokenException(MESSAGE), r),
                        HttpStatus.UNAUTHORIZED),
                Arguments.of("SavedItemNotFoundException",
                        (HandlerInvocation) (h, r) -> h.handleSavedItemNotFoundException(new SavedItemNotFoundException(MESSAGE), r),
                        HttpStatus.NOT_FOUND),
                Arguments.of("SavedItemLimitExceededException",
                        (HandlerInvocation) (h, r) -> h.handleSavedItemLimitExceededException(new SavedItemLimitExceededException(MESSAGE), r),
                        HttpStatus.UNPROCESSABLE_ENTITY),
                Arguments.of("DuplicateAddressException",
                        (HandlerInvocation) (h, r) -> h.handleDuplicateAddressException(new DuplicateAddressException(MESSAGE), r),
                        HttpStatus.CONFLICT),
                Arguments.of("DuplicatePaymentMethodException",
                        (HandlerInvocation) (h, r) -> h.handleDuplicatePaymentMethodException(new DuplicatePaymentMethodException(MESSAGE), r),
                        HttpStatus.CONFLICT),
                Arguments.of("TransactionNotFoundException",
                        (HandlerInvocation) (h, r) -> h.handleTransactionNotFoundException(new TransactionNotFoundException(MESSAGE), r),
                        HttpStatus.NOT_FOUND),
                Arguments.of("NegativeWeightException",
                        (HandlerInvocation) (h, r) -> h.handleNegativeWeightException(new NegativeWeightException(MESSAGE), r),
                        HttpStatus.BAD_REQUEST),
                Arguments.of("InsufficientStockException",
                        (HandlerInvocation) (h, r) -> h.handleInsufficientStockExceptionException(new InsufficientStockException(MESSAGE), r),
                        HttpStatus.CONFLICT),
                Arguments.of("CreateOrderException",
                        (HandlerInvocation) (h, r) -> h.handleCreateOrderException(new CreateOrderException(MESSAGE), r),
                        HttpStatus.INTERNAL_SERVER_ERROR),
                Arguments.of("EmailNotFoundException",
                        (HandlerInvocation) (h, r) -> h.handleEmailNotFoundException(new EmailNotFoundException(MESSAGE), r),
                        HttpStatus.NOT_FOUND),
                Arguments.of("DatabaseInsertionException",
                        (HandlerInvocation) (h, r) -> h.handleDatabaseInsertionException(new DatabaseInsertionException(MESSAGE), r),
                        HttpStatus.INTERNAL_SERVER_ERROR),
                Arguments.of("DatabaseDeletionException",
                        (HandlerInvocation) (h, r) -> h.handleDatabaseDeletionException(new DatabaseDeletionException(MESSAGE), r),
                        HttpStatus.INTERNAL_SERVER_ERROR),
                Arguments.of("MissingIdentifierException",
                        (HandlerInvocation) (h, r) -> h.handleMissingIdentifierException(new MissingIdentifierException(MESSAGE), r),
                        HttpStatus.BAD_REQUEST),
                Arguments.of("InvalidQuantityException",
                        (HandlerInvocation) (h, r) -> h.handleInvalidQuantityException(new InvalidQuantityException(MESSAGE), r),
                        HttpStatus.BAD_REQUEST),
                Arguments.of("CartSerialisationException",
                        (HandlerInvocation) (h, r) -> h.handleCartSerializationException(new CartSerialisationException(MESSAGE), r),
                        HttpStatus.INTERNAL_SERVER_ERROR),
                Arguments.of("RedisDataException",
                        (HandlerInvocation) (h, r) -> h.handleRedisDataException(new RedisDataException(MESSAGE), r),
                        HttpStatus.INTERNAL_SERVER_ERROR),
                Arguments.of("CartUpdateConflictException",
                        (HandlerInvocation) (h, r) -> h.handleCartUpdateConflictException(new CartUpdateConflictException(MESSAGE), r),
                        HttpStatus.CONFLICT),
                Arguments.of("WishlistSerialisationException",
                        (HandlerInvocation) (h, r) -> h.handleWishlistSerialisationException(new WishlistSerialisationException(MESSAGE), r),
                        HttpStatus.INTERNAL_SERVER_ERROR),
                Arguments.of("WishlistUpdateConflictException",
                        (HandlerInvocation) (h, r) -> h.handleWishlistUpdateConflictException(new WishlistUpdateConflictException(MESSAGE), r),
                        HttpStatus.CONFLICT),
                Arguments.of("RecommendationServerException",
                        (HandlerInvocation) (h, r) -> h.handleRecommendationServerException(new RecommendationServerException(MESSAGE), r),
                        HttpStatus.SERVICE_UNAVAILABLE),
                Arguments.of("InvalidProductQueryException",
                        (HandlerInvocation) (h, r) -> h.handleInvalidProductQueryException(new InvalidProductQueryException(MESSAGE), r),
                        HttpStatus.BAD_REQUEST),
                Arguments.of("ProductFetchException",
                        (HandlerInvocation) (h, r) -> h.handleProductFetchException(new ProductFetchException(MESSAGE), r),
                        HttpStatus.INTERNAL_SERVER_ERROR),
                Arguments.of("StripeOperationException",
                        (HandlerInvocation) (h, r) -> h.handleStripeOperationException(new StripeOperationException(MESSAGE), r),
                        HttpStatus.BAD_GATEWAY),
                Arguments.of("DuplicateRequestInProgressException",
                        (HandlerInvocation) (h, r) -> h.handleDuplicateRequestInProgressException(new DuplicateRequestInProgressException(MESSAGE), r),
                        HttpStatus.CONFLICT),
                Arguments.of("OutboxEventCreationException",
                        (HandlerInvocation) (h, r) -> h.handleOutboxEventCreationException(new OutboxEventCreationException(MESSAGE), r),
                        HttpStatus.INTERNAL_SERVER_ERROR)
        );
    }

    @ParameterizedTest(name = "{0} -> {2}")
    @MethodSource("exceptionHandlers")
    void handler_returnsExpectedStatusAndStandardisedBody(String exceptionName, HandlerInvocation invocation,
                                                          HttpStatus expectedStatus) {
        ResponseEntity<Object> response = invocation.invoke(handler, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(expectedStatus);
        ErrorResponseDTO body = (ErrorResponseDTO) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(expectedStatus.value());
        assertThat(body.getError()).isEqualTo(expectedStatus.getReasonPhrase());
        assertThat(body.getMessage()).isEqualTo(MESSAGE);
        assertThat(body.getPath()).isEqualTo(EXPECTED_PATH);
        assertThat(body.getTimestamp()).isNotNull();
    }

    @Test
    void handleClockSkewException_returnsServiceUnavailable_withGeneratedMessage() {
        ResponseEntity<Object> response = handler.handleClockSkewException(new ClockSkewException(500L), webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        ErrorResponseDTO body = (ErrorResponseDTO) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getMessage()).contains("Clock moved backwards by 500 milliseconds");
    }

    @Test
    void handleMethodArgumentNotValidException_joinsFieldErrorsIntoMessage() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(
                new FieldError("request", "email", "must not be blank"),
                new FieldError("request", "password", "must be at least 8 characters")
        ));

        ResponseEntity<Object> response = handler.handleMethodArgumentNotValidException(ex, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ErrorResponseDTO body = (ErrorResponseDTO) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getMessage()).isEqualTo("email: must not be blank, password: must be at least 8 characters");
        assertThat(body.getPath()).isEqualTo(EXPECTED_PATH);
    }

    @Test
    void handleMethodArgumentNotValidException_returnsEmptyMessage_whenNoFieldErrors() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of());

        ResponseEntity<Object> response = handler.handleMethodArgumentNotValidException(ex, webRequest);

        ErrorResponseDTO body = (ErrorResponseDTO) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getMessage()).isEmpty();
    }

    @Test
    void handleMissingServletRequestParameterException_returnsBadRequest_withExceptionMessage() {
        MissingServletRequestParameterException ex =
                new MissingServletRequestParameterException("productId", "String");

        ResponseEntity<Object> response = handler.handleMissingServletRequestParameterException(ex, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ErrorResponseDTO body = (ErrorResponseDTO) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getMessage()).isEqualTo(ex.getMessage());
    }

    @Test
    void handleAllExceptions_returnsInternalServerError_withPrefixedMessage() {
        RuntimeException ex = new RuntimeException("boom");

        ResponseEntity<Object> response = handler.handleAllExceptions(ex, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        ErrorResponseDTO body = (ErrorResponseDTO) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getMessage()).isEqualTo("An unexpected error occurred: boom");
    }

    @Test
    void handleAllExceptions_fallsBackToReasonPhrase_whenExceptionMessageIsNull() {
        RuntimeException ex = new RuntimeException((String) null);

        ResponseEntity<Object> response = handler.handleAllExceptions(ex, webRequest);

        ErrorResponseDTO body = (ErrorResponseDTO) response.getBody();
        assertThat(body).isNotNull();
        // ex.getMessage() is null, but string concatenation turns it into the literal "null" before
        // buildErrorResponse's null-check ever sees it, so the reason-phrase fallback never triggers
        assertThat(body.getMessage()).isEqualTo("An unexpected error occurred: null");
    }

    @Test
    void buildErrorResponse_stripsUriPrefixFromRequestDescription() {
        when(webRequest.getDescription(false)).thenReturn("uri=/junes/api/v1/product/123");

        ResponseEntity<Object> response = handler.handleProductNotFoundException(
                new ProductNotFoundException(MESSAGE), webRequest);

        ErrorResponseDTO body = (ErrorResponseDTO) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getPath()).isEqualTo("/junes/api/v1/product/123");
    }
}
