package com.gamersblended.junes.service;

import com.gamersblended.junes.dto.AddressDTO;
import com.gamersblended.junes.dto.EmailRequestDTO;
import com.gamersblended.junes.dto.TransactionItemEmailDTO;
import com.gamersblended.junes.exception.InvalidTemplateException;
import com.gamersblended.junes.exception.QueueEmailException;
import com.gamersblended.junes.model.Product;
import com.gamersblended.junes.model.Transaction;
import com.gamersblended.junes.model.TransactionItem;
import eu.bitwalker.useragentutils.UserAgent;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    @Value("${baseURL:}")
    private String baseURL;

    @Value("${app.support.email:support@junes.com}")
    private String supportEmail;

    private final RabbitTemplate rabbitTemplate;
    private final TemplateEngine templateEngine;
    private final GeoLocationService geoLocationService;
    public static final String ORDER_DETAILS_ENDPOINT = "/junes/api/v1/transaction/";

    public EmailProducerService(RabbitTemplate rabbitTemplate, TemplateEngine templateEngine, GeoLocationService geoLocationService) {
        this.rabbitTemplate = rabbitTemplate;
        this.templateEngine = templateEngine;
        this.geoLocationService = geoLocationService;
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

    public void sendPasswordChangedEmail(String toEmail, HttpServletRequest request) {
        log.info("Queuing for password changed email for: {}", toEmail);

        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("E, MMM dd yyyy, h:mma");
        String when = now.format(formatter);

        String ip = geoLocationService.getClientIp(request);
        String location = geoLocationService.getLocation(ip);

        String userAgentString = request.getHeader("User-Agent");
        UserAgent userAgent = UserAgent.parseUserAgentString(userAgentString);
        String deviceType = userAgent.getBrowser().getName() + " using " +
                userAgent.getOperatingSystem().getName();

        try {
            Map<String, Object> variables = getCommonVariableMap();
            variables.put("when", when);
            variables.put("where", location);
            variables.put("deviceType", deviceType);

            String htmlContent = processTemplate(PASSWORD_CHANGED, variables);

            EmailRequestDTO emailRequestDTO = getEmailRequest(htmlContent, toEmail, "Password Changed - " + appName);

            rabbitTemplate.convertAndSend(exchange, routingKey, emailRequestDTO);
            log.info("Password changed email queued successfully for: {}", toEmail);
        } catch (Exception ex) {
            log.error("Exception in queuing password changed email for {}: ", toEmail, ex);
            throw new QueueEmailException("Failed to queue password changed email");
        }
    }

    public void sendOrderConfirmedEmail(String toEmail, Transaction transaction, Map<String, Product> productMap, AddressDTO addressDTO) {
        log.info("Queuing for order confirmed email for: {}", toEmail);

        List<TransactionItemEmailDTO> itemList = new ArrayList<>();
        for (TransactionItem currentItem : transaction.getItems()) {
            TransactionItemEmailDTO emailItem = new TransactionItemEmailDTO();

            Product productMetadata = productMap.get(currentItem.getProductID());
            if (null != productMetadata) {
                emailItem.setName(productMetadata.getName());
                emailItem.setPrice(productMetadata.getPrice());
                emailItem.setProductImageUrl(productMetadata.getProductImageUrl());
            }
            emailItem.setQuantity(currentItem.getQuantity());

            itemList.add(emailItem);
        }

        String orderDetailsUrl = baseURL + ORDER_DETAILS_ENDPOINT + transaction.getTransactionID() + "/details";
        try {
            Map<String, Object> variables = getCommonVariableMap();
            variables.put("orderNumber", transaction.getOrderNumber());
            variables.put("transactionID", transaction.getTransactionID());
            variables.put("itemList", itemList);
            variables.put("address", addressDTO);
            variables.put("shippingCost", transaction.getShippingCost());
            variables.put("totalAmount", transaction.getTotalAmount());
            variables.put("appUrl", appUrl);
            variables.put("orderDetailsUrl", orderDetailsUrl);

            String htmlContent = processTemplate(ORDER_CONFIRMED, variables);

            EmailRequestDTO emailRequestDTO = getEmailRequest(htmlContent, toEmail, "Order#" + transaction.getOrderNumber() + " Confirmed!");

            rabbitTemplate.convertAndSend(exchange, routingKey, emailRequestDTO);
            log.info("Order confirmed email queued successfully for: {}", toEmail);
        } catch (Exception ex) {
            log.error("Exception in queuing order confirmed email for {}: ", toEmail, ex);
            throw new QueueEmailException("Failed to queue order confirmed email");
        }
    }

    private Map<String, Object> getCommonVariableMap() {

        Map<String, Object> variables = new HashMap<>();
        variables.put("appName", appName);
        variables.put("supportEmail", supportEmail);

        return variables;
    }

    private String processTemplate(String type, Map<String, Object> variables) {

        Context context = new Context();
        context.setVariables(variables);

        return switch (type) {
            case VERIFICATION -> templateEngine.process("email/verification", context);
            case PASSWORD_RESET -> templateEngine.process("email/password-reset", context);
            case PASSWORD_CHANGED -> templateEngine.process("email/password-changed", context);
            case WELCOME -> templateEngine.process("email/welcome", context);
            case ORDER_CONFIRMED -> templateEngine.process("email/order-confirmed", context);
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
