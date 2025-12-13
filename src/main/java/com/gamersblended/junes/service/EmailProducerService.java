package com.gamersblended.junes.service;

import com.gamersblended.junes.dto.EmailRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class EmailProducerService {

    @Value("${email.queue.exchange}")
    private String exchange;

    @Value("${email.queue.routing-key}")
    private String routingKey;

    @Value("${app.name:Junes}")
    private String appName;

    @Value("${app.support.email:support@junes.com}")
    private String supportEmail;

    private final RabbitTemplate rabbitTemplate;
    private final TemplateEngine templateEngine;

    public EmailProducerService(RabbitTemplate rabbitTemplate, TemplateEngine templateEngine) {
        this.rabbitTemplate = rabbitTemplate;
        this.templateEngine = templateEngine;
    }

    public void sendEmailRequest(EmailRequest emailRequest) {
        log.info("Sending email request to queue for: {}", emailRequest.getTo());
        rabbitTemplate.convertAndSend(exchange, routingKey, emailRequest);
        log.info("Email request queued successfully");
    }

    public void sendVerificationEmail(String toEmail, String verificationLink) {
        log.info("Queuing verification email for {}", toEmail);

        try {
            // Prepare template variables
            Map<String, Object> variables = new HashMap<>();
            variables.put("appName", appName);
            variables.put("supportEmail", appName);
            variables.put("verificationLink", verificationLink);

            // Process template
            Context context = new Context();
            context.setVariables(variables);
            String htmlContent = templateEngine.process("email/verification", context);

            // Create email request
            EmailRequest emailRequest = EmailRequest.builder()
                    .to(toEmail)
                    .subject("Verify Your Email - " + appName)
                    .body(htmlContent)
                    .build();

            // Send to queue
            rabbitTemplate.convertAndSend(exchange, routingKey, emailRequest);
            log.info("Verification email queued successfully for: {}", toEmail);
        } catch (Exception ex) {
            log.error("Exception in queuing verification email for {}: {}", toEmail, ex.getMessage(), ex);
            throw new RuntimeException("Failed to queue verification email", ex);
        }
    }
}
