package com.gamersblended.junes.service;

import com.gamersblended.junes.model.User;
import com.gamersblended.junes.repository.jpa.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserVerificationWriter {

    private final UserRepository userRepository;

    public UserVerificationWriter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public void completeSignupVerification(User user, String stripeCustomerID) {
        user.setIsEmailVerified(true);
        user.setStripeCustomerID(stripeCustomerID);
        userRepository.saveAndFlush(user);
    }

    @Transactional
    public void completeEmailChange(User user, String newEmail) {
        user.setEmail(newEmail);
        userRepository.saveAndFlush(user);
    }
}
