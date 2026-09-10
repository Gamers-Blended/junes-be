package com.gamersblended.junes.service.email;

import com.gamersblended.junes.dto.EmailRequestDTO;
import com.gamersblended.junes.exception.EmailDeliveryException;
import com.mailgun.api.v3.MailgunMessagesApi;
import com.mailgun.model.message.Message;
import com.mailgun.model.message.MessageResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailConsumerServiceTest {

    private static final String DOMAIN = "mg.junes.test";
    private static final String FROM_EMAIL = "noreply@junes.test";

    @Mock
    private MailgunMessagesApi mailgunMessagesApi;

    private EmailConsumerService emailConsumerService;

    @BeforeEach
    void setUp() {
        emailConsumerService = new EmailConsumerService("test-api-key");
        ReflectionTestUtils.setField(emailConsumerService, "mailgunMessagesApi", mailgunMessagesApi);
        ReflectionTestUtils.setField(emailConsumerService, "domain", DOMAIN);
        ReflectionTestUtils.setField(emailConsumerService, "fromEmail", FROM_EMAIL);
    }

    @AfterEach
    void clearInterruptFlag() {
        // One test deliberately interrupts the current thread to short-circuit a retry sleep
        Thread.interrupted();
    }

    private static EmailRequestDTO emailRequest(String subject) {
        return EmailRequestDTO.builder().to("player@example.com").subject(subject).body("Welcome").build();
    }

    // ---- consumeEmailRequest: success on first attempt ----

    @Test
    void consumeEmailRequest_sendsEmail_onFirstAttempt() {
        EmailRequestDTO dto = emailRequest("<p>Hello &amp; welcome</p>");
        when(mailgunMessagesApi.sendMessage(eq(DOMAIN), any(Message.class)))
                .thenReturn(MessageResponse.builder().id("msg-1").build());

        emailConsumerService.consumeEmailRequest(dto);

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(mailgunMessagesApi, times(1)).sendMessage(eq(DOMAIN), captor.capture());
        Message sentMessage = captor.getValue();
        assertThat(sentMessage.getFrom()).isEqualTo(FROM_EMAIL);
        assertThat(sentMessage.getTo()).containsExactly("player@example.com");
        assertThat(sentMessage.getSubject()).isEqualTo("Welcome");
        assertThat(sentMessage.getHtml()).isEqualTo("<p>Hello &amp; welcome</p>");
        assertThat(sentMessage.getReplyTo()).isEqualTo(FROM_EMAIL);
        assertThat(sentMessage.getText()).isEqualTo("Hello & welcome");
    }

    // ---- consumeEmailRequest: retry behavior ----

    @Test
    void consumeEmailRequest_retriesAndSucceeds_afterRetryableFailure() {
        EmailRequestDTO dto = emailRequest("<p>Hi</p>");
        when(mailgunMessagesApi.sendMessage(eq(DOMAIN), any(Message.class)))
                .thenThrow(new RuntimeException()) // no message -> not classified as non-retryable
                .thenReturn(MessageResponse.builder().id("msg-2").build());

        emailConsumerService.consumeEmailRequest(dto);

        verify(mailgunMessagesApi, times(2)).sendMessage(eq(DOMAIN), any(Message.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"403 Forbidden", "400 Bad Request", "401 Unauthorized", "404 Not Found"})
    void consumeEmailRequest_stopsImmediately_onNonRetryableError(String errorMessage) {
        EmailRequestDTO dto = emailRequest("<p>Hi</p>");
        when(mailgunMessagesApi.sendMessage(eq(DOMAIN), any(Message.class)))
                .thenThrow(new RuntimeException(errorMessage));

        assertThatThrownBy(() -> emailConsumerService.consumeEmailRequest(dto))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessageContaining("player@example.com");

        verify(mailgunMessagesApi, times(1)).sendMessage(eq(DOMAIN), any(Message.class));
    }

    @Test
    void consumeEmailRequest_throwsEmailDeliveryException_afterAllRetriesExhausted() {
        EmailRequestDTO dto = emailRequest("<p>Hi</p>");
        when(mailgunMessagesApi.sendMessage(eq(DOMAIN), any(Message.class)))
                .thenThrow(new RuntimeException("503 Service Unavailable"));

        assertThatThrownBy(() -> emailConsumerService.consumeEmailRequest(dto))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessageContaining("player@example.com");

        verify(mailgunMessagesApi, times(3)).sendMessage(eq(DOMAIN), any(Message.class));
    }

    @Test
    void consumeEmailRequest_throwsEmailDeliveryException_whenRetrySleepIsInterrupted() {
        EmailRequestDTO dto = emailRequest("<p>Hi</p>");
        when(mailgunMessagesApi.sendMessage(eq(DOMAIN), any(Message.class)))
                .thenThrow(new RuntimeException("503 Service Unavailable"));
        Thread.currentThread().interrupt();

        assertThatThrownBy(() -> emailConsumerService.consumeEmailRequest(dto))
                .isInstanceOf(EmailDeliveryException.class);

        // interrupted while sleeping before a retry -> only the first attempt was made
        verify(mailgunMessagesApi, times(1)).sendMessage(eq(DOMAIN), any(Message.class));
    }
}
