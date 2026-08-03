package com.gamersblended.junes.service;

import com.gamersblended.junes.constant.TokenPurpose;
import com.gamersblended.junes.exception.*;
import com.gamersblended.junes.model.EmailVerificationToken;
import com.gamersblended.junes.model.User;
import com.gamersblended.junes.repository.jpa.EmailVerificationTokenRepository;
import com.gamersblended.junes.repository.jpa.UserRepository;
import com.stripe.exception.StripeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.UUID;

import static com.gamersblended.junes.constant.ConfigSettingsConstants.CHANGE_EMAIL_EXPIRY_HOURS;
import static com.gamersblended.junes.constant.ConfigSettingsConstants.SIGNUP_EMAIL_EXPIRY_HOURS;
import static com.gamersblended.junes.util.TokenUtils.hashToken;

@Slf4j
@Service
public class EmailVerificationTokenService {

    private final StripeService stripeService;
    private final SecureRandom secureRandom;
    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;

    public EmailVerificationTokenService(StripeService stripeService, SecureRandom secureRandom, UserRepository userRepository, EmailVerificationTokenRepository emailVerificationTokenRepository) {
        this.stripeService = stripeService;
        this.secureRandom = secureRandom;
        this.userRepository = userRepository;
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
    }

    @Transactional
    public String generateVerificationToken(UUID userID, String email, TokenPurpose purpose) throws NoSuchAlgorithmException {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        String tokenHash = hashToken(token);
        LocalDateTime expiryDate = switch (purpose) {
            case SIGNUP_EMAIL -> LocalDateTime.now(ZoneId.of("Asia/Singapore")).plusHours(SIGNUP_EMAIL_EXPIRY_HOURS);
            case CHANGE_EMAIL -> LocalDateTime.now(ZoneId.of("Asia/Singapore")).plusHours(CHANGE_EMAIL_EXPIRY_HOURS);
        };

        emailVerificationTokenRepository.invalidateActiveTokens(userID, purpose);
        EmailVerificationToken emailVerificationToken = new EmailVerificationToken();
        emailVerificationToken.setUserID(userID);
        emailVerificationToken.setEmail(email);
        emailVerificationToken.setTokenHash(tokenHash);
        emailVerificationToken.setPurpose(purpose);
        emailVerificationToken.setExpiryDate(expiryDate);
        emailVerificationToken.setUsed(false);
        emailVerificationTokenRepository.save(emailVerificationToken);

        // Raw token used in email link, hash used in DB
        return token;
    }

    public void markSignupVerified(EmailVerificationToken token) throws StripeException {
        User user = userRepository.getUserByID(token.getUserID())
                .orElseThrow(() -> new UserNotFoundException("User not found: " + token.getUserID()));

        // Check if user is already verified
        if (Boolean.TRUE.equals(user.getIsEmailVerified())) {
            log.error("{} is already verified", user.getEmail());
            throw new EmailAlreadyVerifiedException(user.getEmail() + " is already verified");
        }

        // Check if email has already been verified in database
        if (Boolean.TRUE.equals(userRepository.isEmailVerified(token.getEmail()))) {
            throw new EmailAlreadyInUseException("New email " + token.getEmail() + " is already in use, please use another email");
        }

        // Check if user's email changed before signup is verified
        if (!user.getEmail().equals(token.getEmail())) {
            log.error("User's email {} does not match token's email: {}", user.getEmail(), token.getEmail());
            throw new InvalidTokenException("Invalid or expired token");
        }

        String stripeCustomerID = stripeService.createCustomer(user.getEmail(), user.getUserID());

        user.setIsEmailVerified(true);
        user.setStripeCustomerID(stripeCustomerID);
        userRepository.save(user);
    }

    public void markEmailChangeVerified(EmailVerificationToken token) {
        User user = userRepository.getUserByID(token.getUserID())
                .orElseThrow(() -> new UserNotFoundException("User not found: " + token.getUserID()));

        // Check if email has already been verified in database
        if (Boolean.TRUE.equals(userRepository.isEmailVerified(token.getEmail()))) {
            throw new EmailAlreadyInUseException("New email " + token.getEmail() + " is already in use, please use another email");
        }

        user.setEmail(token.getEmail());
        userRepository.save(user);
    }

    @Transactional
    public void cleanupUnverifiedEmails() {
        try {
            int deletedCount = userRepository.deleteAllUnverifiedRecords();

            log.info("Number of unverified emails deleted: {}", deletedCount);
        } catch (Exception ex) {
            log.error("Exception in deleting unverified emails: ", ex);
            throw new DatabaseDeletionException("Exception in deleting unverified emails");
        }
    }
}
