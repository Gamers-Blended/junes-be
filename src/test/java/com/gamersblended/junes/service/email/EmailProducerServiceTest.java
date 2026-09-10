package com.gamersblended.junes.service.email;

import com.gamersblended.junes.constant.TokenPurpose;
import com.gamersblended.junes.dto.AddressDTO;
import com.gamersblended.junes.dto.EmailRequestDTO;
import com.gamersblended.junes.exception.QueueEmailException;
import com.gamersblended.junes.model.Product;
import com.gamersblended.junes.model.Transaction;
import com.gamersblended.junes.model.TransactionItem;
import com.gamersblended.junes.service.GeoLocationService;
import com.gamersblended.junes.util.EmailValueFormatter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.gamersblended.junes.constant.ConfigSettingsConstants.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailProducerServiceTest {

    private static final String EXCHANGE = "email.exchange";
    private static final String ROUTING_KEY = "email.routing-key";
    private static final String APP_NAME = "Junes";
    private static final String APP_URL = "https://junes.test";
    private static final String SUPPORT_EMAIL = "support@junes.test";
    private static final String CHROME_WINDOWS_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/117.0.0.0 Safari/537.36";

    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private TemplateEngine templateEngine;
    @Mock
    private GeoLocationService geoLocationService;
    @Mock
    private EmailValueFormatter emailValueFormatter;

    private EmailProducerService emailProducerService;

    @BeforeEach
    void setUp() {
        emailProducerService = new EmailProducerService(rabbitTemplate, templateEngine, geoLocationService, emailValueFormatter);
        ReflectionTestUtils.setField(emailProducerService, "exchange", EXCHANGE);
        ReflectionTestUtils.setField(emailProducerService, "routingKey", ROUTING_KEY);
        ReflectionTestUtils.setField(emailProducerService, "appName", APP_NAME);
        ReflectionTestUtils.setField(emailProducerService, "appUrl", APP_URL);
        ReflectionTestUtils.setField(emailProducerService, "supportEmail", SUPPORT_EMAIL);
    }

    private static Product product(BigDecimal price) {
        return new Product("Great Game", "great-game", "description", price, "ps5", "us", "std",
                "publisher", LocalDate.now(), Set.of(), Set.of(), Set.of(), Set.of(), BigDecimal.ONE, 0, 0,
                "great-game.png", List.of(), LocalDate.now());
    }

    private static TransactionItem transactionItem(String productID, int quantity) {
        TransactionItem item = new TransactionItem();
        item.setProductID(productID);
        item.setQuantity(quantity);
        return item;
    }

    private static Transaction transaction(String orderNumber, List<TransactionItem> items) {
        Transaction transaction = new Transaction();
        transaction.setTransactionID(UUID.randomUUID());
        transaction.setOrderNumber(orderNumber);
        transaction.setItems(items);
        transaction.setShippingCost(BigDecimal.valueOf(5.99));
        transaction.setTotalAmount(BigDecimal.valueOf(59.98));
        return transaction;
    }

    private static AddressDTO addressDTO() {
        return new AddressDTO(UUID.randomUUID(), "John Doe", "123 Main St", "unit 1",
                "US", "12345", "+1234567890", true);
    }

    private void stubTemplateProcessing(String templateName, String htmlContent) {
        when(templateEngine.process(eq(templateName), any(Context.class))).thenReturn(htmlContent);
    }

    // ---- sendVerificationEmail ----

    @Test
    void sendVerificationEmail_queuesEmail_forSignupPurpose() {
        stubTemplateProcessing("email/verification", "<html>verify</html>");

        emailProducerService.sendVerificationEmail("player@example.com", "https://junes.test/verify?token=abc", TokenPurpose.SIGNUP_EMAIL);

        Context context = captureContext("email/verification");
        assertThat(context.getVariable("verificationLink")).isEqualTo("https://junes.test/verify?token=abc");
        assertThat(context.getVariable("expirationHours")).isEqualTo(SIGNUP_EMAIL_EXPIRY_HOURS);
        assertCommonVariables(context);

        EmailRequestDTO dto = captureQueuedEmail();
        assertThat(dto.getTo()).isEqualTo("player@example.com");
        assertThat(dto.getSubject()).isEqualTo("Verify Your Email - " + APP_NAME);
        assertThat(dto.getBody()).isEqualTo("<html>verify</html>");
    }

    @Test
    void sendVerificationEmail_queuesEmail_forChangeEmailPurpose() {
        stubTemplateProcessing("email/verification", "<html>verify</html>");

        emailProducerService.sendVerificationEmail("player@example.com", "https://junes.test/verify?token=abc", TokenPurpose.CHANGE_EMAIL);

        Context context = captureContext("email/verification");
        assertThat(context.getVariable("expirationHours")).isEqualTo(CHANGE_EMAIL_EXPIRY_HOURS);
    }

    @Test
    void sendVerificationEmail_throwsQueueEmailException_whenTemplateEngineThrows() {
        when(templateEngine.process(anyString(), any(Context.class))).thenThrow(new RuntimeException("template error"));

        assertThatThrownBy(() -> emailProducerService.sendVerificationEmail("player@example.com", "link", TokenPurpose.SIGNUP_EMAIL))
                .isInstanceOf(QueueEmailException.class);

        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void sendVerificationEmail_throwsQueueEmailException_whenRabbitTemplateThrows() {
        stubTemplateProcessing("email/verification", "<html>verify</html>");
        doThrow(new RuntimeException("broker down")).when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(EmailRequestDTO.class));

        assertThatThrownBy(() -> emailProducerService.sendVerificationEmail("player@example.com", "link", TokenPurpose.SIGNUP_EMAIL))
                .isInstanceOf(QueueEmailException.class);
    }

    // ---- sendPasswordResetEmail ----

    @Test
    void sendPasswordResetEmail_queuesEmail_successfully() {
        stubTemplateProcessing("email/password-reset", "<html>reset</html>");

        emailProducerService.sendPasswordResetEmail("player@example.com", "https://junes.test/reset?token=xyz");

        Context context = captureContext("email/password-reset");
        assertThat(context.getVariable("resetLink")).isEqualTo("https://junes.test/reset?token=xyz");
        assertThat(context.getVariable("expiryHours")).isEqualTo(RESET_PASSWORD_EXPIRY_HOURS);
        assertCommonVariables(context);

        EmailRequestDTO dto = captureQueuedEmail();
        assertThat(dto.getSubject()).isEqualTo("Password Reset Request - " + APP_NAME);
        assertThat(dto.getBody()).isEqualTo("<html>reset</html>");
    }

    @Test
    void sendPasswordResetEmail_throwsQueueEmailException_onFailure() {
        when(templateEngine.process(anyString(), any(Context.class))).thenThrow(new RuntimeException("template error"));

        assertThatThrownBy(() -> emailProducerService.sendPasswordResetEmail("player@example.com", "link"))
                .isInstanceOf(QueueEmailException.class);

        verifyNoInteractions(rabbitTemplate);
    }

    // ---- sendWelcomeEmail ----

    @Test
    void sendWelcomeEmail_queuesEmail_successfully() {
        stubTemplateProcessing("email/welcome", "<html>welcome</html>");

        emailProducerService.sendWelcomeEmail("player@example.com");

        Context context = captureContext("email/welcome");
        assertThat(context.getVariable("appUrl")).isEqualTo(APP_URL);
        assertCommonVariables(context);

        EmailRequestDTO dto = captureQueuedEmail();
        assertThat(dto.getSubject()).isEqualTo("Welcome to " + APP_NAME + "!");
    }

    @Test
    void sendWelcomeEmail_throwsQueueEmailException_onFailure() {
        when(templateEngine.process(anyString(), any(Context.class))).thenThrow(new RuntimeException("template error"));

        assertThatThrownBy(() -> emailProducerService.sendWelcomeEmail("player@example.com"))
                .isInstanceOf(QueueEmailException.class);

        verifyNoInteractions(rabbitTemplate);
    }

    // ---- sendPasswordChangedEmail ----

    @Test
    void sendPasswordChangedEmail_queuesEmail_successfully() {
        stubTemplateProcessing("email/password-changed", "<html>changed</html>");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", CHROME_WINDOWS_USER_AGENT);
        when(geoLocationService.getClientIp(request)).thenReturn("203.0.113.1");
        when(geoLocationService.getLocation("203.0.113.1")).thenReturn("Singapore, Asia, Singapore");

        emailProducerService.sendPasswordChangedEmail("player@example.com", request);

        Context context = captureContext("email/password-changed");
        assertThat((String) context.getVariable("when")).matches("^\\w{3}, \\w{3} \\d{2} \\d{4}, \\d{1,2}:\\d{2}[ap]m$");
        assertThat(context.getVariable("where")).isEqualTo("Singapore, Asia, Singapore");
        assertThat(context.getVariable("deviceType")).isEqualTo("Chrome 11 using Windows 10");
        assertCommonVariables(context);

        EmailRequestDTO dto = captureQueuedEmail();
        assertThat(dto.getSubject()).isEqualTo("Password Changed - " + APP_NAME);
    }

    @Test
    void sendPasswordChangedEmail_throwsQueueEmailException_onFailure() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", CHROME_WINDOWS_USER_AGENT);
        when(geoLocationService.getClientIp(request)).thenReturn("203.0.113.1");
        when(geoLocationService.getLocation("203.0.113.1")).thenReturn("Singapore, Asia, Singapore");
        when(templateEngine.process(anyString(), any(Context.class))).thenThrow(new RuntimeException("template error"));

        assertThatThrownBy(() -> emailProducerService.sendPasswordChangedEmail("player@example.com", request))
                .isInstanceOf(QueueEmailException.class);

        verifyNoInteractions(rabbitTemplate);
    }

    // ---- sendOrderConfirmedEmail ----

    @Test
    void sendOrderConfirmedEmail_queuesEmail_successfully() {
        TransactionItem item = transactionItem("prod-1", 2);
        Transaction transactionEntity = transaction("ORD-1001", List.of(item));
        Product product = product(BigDecimal.valueOf(29.99));
        Map<String, Product> productMap = Map.of("prod-1", product);
        AddressDTO addressDTO = addressDTO();
        when(emailValueFormatter.formatPlatformName("ps5")).thenReturn("PlayStation 5");
        when(emailValueFormatter.formatRegionName("us")).thenReturn("United States");
        when(emailValueFormatter.formatEditionName("std")).thenReturn("Standard");
        when(emailValueFormatter.appendUrlPrefix("great-game.png")).thenReturn("https://cdn.junes.test/great-game.png");
        stubTemplateProcessing("email/order-confirmed", "<html>confirmed</html>");

        emailProducerService.sendOrderConfirmedEmail("player@example.com", transactionEntity, productMap, addressDTO);

        Context context = captureContext("email/order-confirmed");
        assertThat(context.getVariable("orderNumber")).isEqualTo("ORD-1001");
        assertThat(context.getVariable("transactionID")).isEqualTo(transactionEntity.getTransactionID());
        assertThat(context.getVariable("address")).isSameAs(addressDTO);
        assertThat(context.getVariable("shippingCost")).isEqualTo(BigDecimal.valueOf(5.99));
        assertThat(context.getVariable("totalAmount")).isEqualTo(BigDecimal.valueOf(59.98));
        assertThat(context.getVariable("appUrl")).isEqualTo(APP_URL);
        assertThat(context.getVariable("orderDetailsUrl")).isEqualTo(APP_URL + "/order/ORD-1001");
        assertCommonVariables(context);

        @SuppressWarnings("unchecked")
        List<com.gamersblended.junes.dto.TransactionItemEmailDTO> itemList =
                (List<com.gamersblended.junes.dto.TransactionItemEmailDTO>) context.getVariable("itemList");
        assertThat(itemList).hasSize(1);
        com.gamersblended.junes.dto.TransactionItemEmailDTO emailItem = itemList.get(0);
        assertThat(emailItem.getName()).isEqualTo("Great Game");
        assertThat(emailItem.getPlatform()).isEqualTo("PlayStation 5");
        assertThat(emailItem.getRegion()).isEqualTo("United States");
        assertThat(emailItem.getEdition()).isEqualTo("Standard");
        assertThat(emailItem.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(29.99));
        assertThat(emailItem.getQuantity()).isEqualTo(2);
        assertThat(emailItem.getProductImageUrl()).isEqualTo("https://cdn.junes.test/great-game.png");

        EmailRequestDTO dto = captureQueuedEmail();
        assertThat(dto.getSubject()).isEqualTo("Order#ORD-1001 Confirmed!");
    }

    @Test
    void sendOrderConfirmedEmail_addsPlaceholderItem_whenProductMetadataMissing() {
        TransactionItem item = transactionItem("prod-missing", 3);
        Transaction transactionEntity = transaction("ORD-1002", List.of(item));
        Map<String, Product> productMap = Map.of();
        stubTemplateProcessing("email/order-confirmed", "<html>confirmed</html>");

        emailProducerService.sendOrderConfirmedEmail("player@example.com", transactionEntity, productMap, addressDTO());

        Context context = captureContext("email/order-confirmed");
        @SuppressWarnings("unchecked")
        List<com.gamersblended.junes.dto.TransactionItemEmailDTO> itemList =
                (List<com.gamersblended.junes.dto.TransactionItemEmailDTO>) context.getVariable("itemList");
        assertThat(itemList).hasSize(1);
        com.gamersblended.junes.dto.TransactionItemEmailDTO emailItem = itemList.get(0);
        assertThat(emailItem.getName()).isNull();
        assertThat(emailItem.getQuantity()).isEqualTo(3);
        verifyNoInteractions(emailValueFormatter);
    }

    @Test
    void sendOrderConfirmedEmail_throwsQueueEmailException_onFailure() {
        Transaction transactionEntity = transaction("ORD-1001", List.of());
        Map<String, Product> emptyProductMap = Map.of();
        AddressDTO addressDTO = addressDTO();
        when(templateEngine.process(anyString(), any(Context.class))).thenThrow(new RuntimeException("template error"));

        assertThatThrownBy(() -> emailProducerService.sendOrderConfirmedEmail("player@example.com", transactionEntity, emptyProductMap, addressDTO))
                .isInstanceOf(QueueEmailException.class);

        verifyNoInteractions(rabbitTemplate);
    }

    // ---- sendOrderShippedEmail ----

    @Test
    void sendOrderShippedEmail_queuesEmail_successfully() {
        TransactionItem item = transactionItem("prod-1", 1);
        Transaction transactionEntity = transaction("ORD-2001", List.of(item));
        transactionEntity.setTrackingNumber("TRACK-123");
        transactionEntity.setShippedDate(LocalDateTime.of(2026, Month.SEPTEMBER, 1, 10, 0));
        Product product = product(BigDecimal.valueOf(29.99));
        Map<String, Product> productMap = Map.of("prod-1", product);
        when(emailValueFormatter.formatPlatformName("ps5")).thenReturn("PlayStation 5");
        when(emailValueFormatter.formatRegionName("us")).thenReturn("United States");
        when(emailValueFormatter.formatEditionName("std")).thenReturn("Standard");
        when(emailValueFormatter.appendUrlPrefix("great-game.png")).thenReturn("https://cdn.junes.test/great-game.png");
        stubTemplateProcessing("email/order-shipped", "<html>shipped</html>");

        emailProducerService.sendOrderShippedEmail("player@example.com", transactionEntity, productMap, addressDTO());

        Context context = captureContext("email/order-shipped");
        assertThat(context.getVariable("orderNumber")).isEqualTo("ORD-2001");
        assertThat(context.getVariable("trackingNumber")).isEqualTo("TRACK-123");
        assertThat(context.getVariable("shippedDate")).isEqualTo(LocalDateTime.of(2026, Month.SEPTEMBER, 1, 10, 0));
        assertThat(context.getVariable("orderDetailsUrl")).isEqualTo(APP_URL + "/order/ORD-2001");

        EmailRequestDTO dto = captureQueuedEmail();
        assertThat(dto.getSubject()).isEqualTo("Order#ORD-2001 Shipped!");
    }

    @Test
    void sendOrderShippedEmail_throwsQueueEmailException_onFailure() {
        Transaction transactionEntity = transaction("ORD-2001", List.of());
        Map<String, Product> emptyProductMap = Map.of();
        AddressDTO addressDTO = addressDTO();
        when(templateEngine.process(anyString(), any(Context.class))).thenThrow(new RuntimeException("template error"));

        assertThatThrownBy(() -> emailProducerService.sendOrderShippedEmail("player@example.com", transactionEntity, emptyProductMap, addressDTO))
                .isInstanceOf(QueueEmailException.class);

        verifyNoInteractions(rabbitTemplate);
    }

    // ---- shared assertion helpers ----

    private void assertCommonVariables(Context context) {
        assertThat(context.getVariable("appName")).isEqualTo(APP_NAME);
        assertThat(context.getVariable("supportEmail")).isEqualTo(SUPPORT_EMAIL);
    }

    private Context captureContext(String templateName) {
        ArgumentCaptor<Context> captor = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq(templateName), captor.capture());
        return captor.getValue();
    }

    private EmailRequestDTO captureQueuedEmail() {
        ArgumentCaptor<EmailRequestDTO> captor = ArgumentCaptor.forClass(EmailRequestDTO.class);
        verify(rabbitTemplate).convertAndSend(eq(EXCHANGE), eq(ROUTING_KEY), captor.capture());
        return captor.getValue();
    }
}
