package com.gamersblended.junes.service;

import com.gamersblended.junes.dto.EmailRequest;
import com.mailgun.api.v3.MailgunMessagesApi;
import com.mailgun.client.MailgunClient;
import com.mailgun.model.message.Message;
import com.mailgun.model.message.MessageResponse;
import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EmailConsumerService {

    @Value("${mailgun.domain}")
    private String domain;

    @Value("${mailgun.from.email}")
    private String fromEmail;

    private final MailgunMessagesApi mailgunMessagesApi;

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 2000;

    public EmailConsumerService(@Value("${mailgun.api.key}") String apiKey) {
        this.mailgunMessagesApi = MailgunClient.config(apiKey).createApi(MailgunMessagesApi.class);
    }

    @RabbitListener(queues = "${email.queue.name}")
    public void consumeEmailRequest(EmailRequest emailRequest) {
        log.info("Received email request from queue: {}", emailRequest.getTo());

        boolean isSentSuccessfully = sendEmailWithRetry(
                emailRequest.getTo(),
                emailRequest.getSubject(),
                emailRequest.getBody()
        );

        if (!isSentSuccessfully) {
            log.error("Failed to send email to: {} after all retry attempts", emailRequest.getTo());
        }
    }

    private boolean sendEmailWithRetry(String to, String subject, String content) {
        int attempt = 0;
        Exception lastException = null;

        while (attempt < MAX_RETRY_ATTEMPTS) {
            try {
                sendEmail(to, subject, content);
                return true;

            } catch (MailException | MessagingException ex) {
                lastException = ex;
                attempt++;

                if (attempt < MAX_RETRY_ATTEMPTS) {
                    log.info("Email send attempt {} failed for {}: {}. Retrying...",
                            attempt, to, ex.getMessage());

                    try {
                        Thread.sleep(RETRY_DELAY_MS * attempt); // Exponential backoff
                    } catch (InterruptedException iex) {
                        Thread.currentThread().interrupt();
                        log.error("Retry sleep interrupted for {}", to);
                        return false;
                    }
                }
            }
        }

        log.error("Failed to send email to {} after {} attempts: {}",
                to, MAX_RETRY_ATTEMPTS, lastException != null ? lastException.getMessage() : "Unknown error");
        return false;
    }

    private void sendEmail(String to, String subject, String content) throws MessagingException, MailException {
        Message message = Message.builder()
                .from(fromEmail)
                .to(to)
                .subject(subject)
                .html(content)
                .text(extractTextFromHtml(content)) // Fallback for plain text clients
                .replyTo(fromEmail)
                .build();

        MessageResponse response = mailgunMessagesApi.sendMessage(domain, message);
        log.info("Email sent successfully to: {}, Message ID: {}", to, response.getId());
    }

    /**
     * Extract plain text from HTML for email clients that don't support HTML
     */
    private String extractTextFromHtml(String html) {
        // HTML to text conversion
        return html.replace("<[^>]*>", "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replaceAll("\\s+", " ")
                .trim();
    }

}
