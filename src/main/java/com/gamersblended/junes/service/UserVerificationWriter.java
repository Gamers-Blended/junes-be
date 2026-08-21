package com.gamersblended.junes.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamersblended.junes.dto.event.StripeEmailUpdateEvent;
import com.gamersblended.junes.exception.OutboxEventCreationException;
import com.gamersblended.junes.model.OutboxEvent;
import com.gamersblended.junes.model.User;
import com.gamersblended.junes.repository.jpa.OutboxEventRepository;
import com.gamersblended.junes.repository.jpa.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static com.gamersblended.junes.constant.KafkaConstants.STRIPE_SYNC_EVENTS;

@Slf4j
@Service
public class UserVerificationWriter {

    private final UserRepository userRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public UserVerificationWriter(UserRepository userRepository, OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void completeSignupVerification(User user, String stripeCustomerID) {
        user.setIsEmailVerified(true);
        user.setStripeCustomerID(stripeCustomerID);
        userRepository.saveAndFlush(user);
    }

    @Transactional
    public void completeEmailChange(User user, String newEmail) {
        // Commit to DB first (real source of truth)
        user.setEmail(newEmail);
        userRepository.saveAndFlush(user);

        // Event to be published to Kafka
        StripeEmailUpdateEvent event = new StripeEmailUpdateEvent();
        event.setEventID(UUID.randomUUID().toString());
        event.setSchemaVersion(1);
        event.setUserID(user.getUserID());
        event.setStripeCustomerID(user.getStripeCustomerID());
        event.setNewEmail(newEmail);

        try {
            OutboxEvent outboxEvent = new OutboxEvent();
            outboxEvent.setAggregateID(user.getUserID().toString());
            outboxEvent.setEventType(event.getEventType());
            outboxEvent.setTopic(STRIPE_SYNC_EVENTS);
            outboxEvent.setPayload(objectMapper.writeValueAsString(event));
            outboxEvent.setStatus("PENDING");
            outboxEvent.setCreatedOn(LocalDateTime.now(ZoneId.of("Asia/Singapore")));
            outboxEvent.setPublished(false);
            outboxEvent.setRetryCount(0);
            outboxEventRepository.save(outboxEvent);
        } catch (Exception ex) {
            log.error("[UserVerificationWriter] Failed to write outbox event for updated email {}", newEmail, ex);
            throw new OutboxEventCreationException("Failed to write outbox event: " + ex.getMessage());
        }
    }
}
