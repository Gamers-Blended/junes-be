package com.gamersblended.junes.service;

import com.gamersblended.junes.dto.EmailRequestDTO;
import com.gamersblended.junes.exception.InvalidTemplateException;
import com.gamersblended.junes.exception.QueueEmailException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.HashMap;
import java.util.Map;

import static com.gamersblended.junes.constant.ConfigSettingsConstants.RESET_PASSWORD_EXPIRY_HOURS;
import static com.gamersblended.junes.constant.EmailTemplateConstants.*;

@Slf4j
@Service
public class EmailProducerService {

    @Value("${email.queue.exchange}")
    private String exchange;

    @Value("${email.queue.routing-key}")
    private String routingKey;

    @Value("${app.name:Junes}")
    private String appName;

    @Value("${app.url:}")
    private String appUrl;

    @Value("${app.support.email:support@junes.com}")
    private String supportEmail;

    private final RabbitTemplate rabbitTemplate;
    private final TemplateEngine templateEngine;

    public EmailProducerService(RabbitTemplate rabbitTemplate, TemplateEngine templateEngine) {
        this.rabbitTemplate = rabbitTemplate;
        this.templateEngine = templateEngine;
    }

    public void sendEmailRequest(EmailRequestDTO emailRequestDTO) {
        log.info("Sending email request to queue for: {}", emailRequestDTO.getTo());

        rabbitTemplate.convertAndSend(exchange, routingKey, emailRequestDTO);
        log.info("Email request queued successfully");
    }

    public void sendVerificationEmail(String toEmail, String verificationLink) {
        log.info("Queuing verification email for {}", toEmail);

        try {
            Map<String, Object> variables = getCommonVariableMap();
            variables.put("verificationLink", verificationLink);

            String htmlContent = processTemplate(VERIFICATION, variables);

            EmailRequestDTO emailRequestDTO = getEmailRequest(htmlContent, toEmail, "Verify Your Email - " + appName);

            rabbitTemplate.convertAndSend(exchange, routingKey, emailRequestDTO);
            log.info("Verification email queued successfully for: {}", toEmail);
        } catch (Exception ex) {
            log.error("Exception in queuing verification email for {}: ", toEmail, ex);
            throw new QueueEmailException("Failed to queue verification email");
        }
    }

    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        log.info("Queuing for password reset email for: {}", toEmail);

        try {
            Map<String, Object> variables = getCommonVariableMap();
            variables.put("resetLink", resetLink);
            variables.put("expiryHours", RESET_PASSWORD_EXPIRY_HOURS);

            String htmlContent = processTemplate(PASSWORD_RESET, variables);

            EmailRequestDTO emailRequestDTO = getEmailRequest(htmlContent, toEmail, "Password Reset Request - " + appName);

            rabbitTemplate.convertAndSend(exchange, routingKey, emailRequestDTO);
            log.info("Password reset email queued successfully for: {}", toEmail);
        } catch (Exception ex) {
            log.error("Exception in queuing password reset email for {}: ", toEmail, ex);
            throw new QueueEmailException("Failed to queue password reset email");
        }
    }

    public void sendWelcomeEmail(String toEmail) {
        log.info("Queuing for welcome email for: {}", toEmail);

        try {
            Map<String, Object> variables = getCommonVariableMap();
            variables.put("appUrl", appUrl);

            String htmlContent = processTemplate(WELCOME, variables);

            EmailRequestDTO emailRequestDTO = getEmailRequest(htmlContent, toEmail, "Welcome to " + appName + "!");

            rabbitTemplate.convertAndSend(exchange, routingKey, emailRequestDTO);
            log.info("Welcome email queued successfully for: {}", toEmail);
        } catch (Exception ex) {
            log.error("Exception in queuing welcome email for {}: ", toEmail, ex);
            throw new QueueEmailException("Failed to queue welcome email");
        }
    }

    private Map<String, Object> getCommonVariableMap() {

        Map<String, Object> variables = new HashMap<>();
        variables.put("appName", appName);
        variables.put("supportEmail", appName);

        return variables;
    }

    private String processTemplate(String type, Map<String, Object> variables) {

        Context context = new Context();
        context.setVariables(variables);

        return switch (type) {
            case VERIFICATION -> templateEngine.process("email/verification", context);
            case PASSWORD_RESET -> templateEngine.process("email/password-reset", context);
            case WELCOME -> templateEngine.process("email/welcome", context);
            default -> {
                log.error("Invalid email template: {}", type);
                throw new InvalidTemplateException("Invalid email template: " + type);
            }
        };
    }

    private EmailRequestDTO getEmailRequest(String htmlContent, String toEmail, String subject) {

        return EmailRequestDTO.builder()
                .to(toEmail)
                .subject(subject)
                .body(htmlContent)
                .build();
    }
}
