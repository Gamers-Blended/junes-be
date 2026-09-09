package com.gamersblended.junes.service.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamersblended.junes.constant.KafkaConstants;
import com.gamersblended.junes.exception.OutboxEventCreationException;
import com.gamersblended.junes.model.OutboxEvent;
import com.gamersblended.junes.model.User;
import com.gamersblended.junes.repository.jpa.OutboxEventRepository;
import com.gamersblended.junes.repository.jpa.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserVerificationWriterTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private ObjectMapper objectMapper;

    private UserVerificationWriter userVerificationWriter;

    @BeforeEach
    void setUp() {
        userVerificationWriter = new UserVerificationWriter(userRepository, outboxEventRepository, objectMapper);
    }

    private static User user(UUID userID) {
        User user = new User();
        user.setUserID(userID);
        return user;
    }

    // ---- completeSignupVerification ----

    @Test
    void completeSignupVerification_marksEmailVerified_andSavesStripeCustomerID() {
        User user = user(UUID.randomUUID());

        userVerificationWriter.completeSignupVerification(user, "cus_123");

        assertThat(user.getIsEmailVerified()).isTrue();
        assertThat(user.getStripeCustomerID()).isEqualTo("cus_123");
        verify(userRepository).saveAndFlush(user);
    }

    // ---- completeEmailChange ----

    @Test
    void completeEmailChange_updatesEmail_andWritesOutboxEvent() throws JsonProcessingException {
        User user = user(UUID.randomUUID());
        user.setStripeCustomerID("cus_123");
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"eventType\":\"EMAIL_UPDATED\"}");

        userVerificationWriter.completeEmailChange(user, "new.email@example.com");

        assertThat(user.getEmail()).isEqualTo("new.email@example.com");
        verify(userRepository).saveAndFlush(user);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        OutboxEvent outboxEvent = captor.getValue();
        assertThat(outboxEvent.getAggregateID()).isEqualTo(user.getUserID().toString());
        assertThat(outboxEvent.getEventType()).isEqualTo(KafkaConstants.EMAIL_UPDATED);
        assertThat(outboxEvent.getTopic()).isEqualTo(KafkaConstants.STRIPE_SYNC_EVENTS);
        assertThat(outboxEvent.getPayload()).isEqualTo("{\"eventType\":\"EMAIL_UPDATED\"}");
        assertThat(outboxEvent.getStatus()).isEqualTo("PENDING");
        assertThat(outboxEvent.isPublished()).isFalse();
        assertThat(outboxEvent.getRetryCount()).isZero();
        assertThat(outboxEvent.getCreatedOn()).isNotNull();
    }

    @Test
    void completeEmailChange_throwsOutboxEventCreationException_whenSerializationFails() throws JsonProcessingException {
        User user = user(UUID.randomUUID());
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("boom") {
        });

        assertThatThrownBy(() -> userVerificationWriter.completeEmailChange(user, "new.email@example.com"))
                .isInstanceOf(OutboxEventCreationException.class);

        verify(outboxEventRepository, never()).save(any());
    }
}
