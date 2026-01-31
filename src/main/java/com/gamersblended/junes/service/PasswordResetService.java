package com.gamersblended.junes.service;

import com.gamersblended.junes.exception.DatabaseDeletionException;
import com.gamersblended.junes.exception.InvalidTokenException;
import com.gamersblended.junes.exception.UserNotFoundException;
import com.gamersblended.junes.model.PasswordResetToken;
import com.gamersblended.junes.model.User;
import com.gamersblended.junes.repository.jpa.PasswordResetTokenRepository;
import com.gamersblended.junes.repository.jpa.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static com.gamersblended.junes.constant.ConfigSettingsConstants.RESET_PASSWORD_EXPIRY_HOURS;

@Slf4j
@Service
public class PasswordResetService {

    @Value("${app.url:}")
    private String appUrl;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final EmailProducerService emailProducerService;
    private final PasswordEncoder passwordEncoder;
    public static final String VERIFY_EMAIL_ENDPOINT = "/resetpassword/";

    public PasswordResetService(UserRepository userRepository, PasswordResetTokenRepository tokenRepository,
                                EmailProducerService emailProducerService, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.emailProducerService = emailProducerService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void initiatePasswordReset(String email) {

        User user = userRepository.getUserByEmail(email)
                .orElseThrow(() -> {
                    log.error("User not found with email: {}", email);
                    return new UserNotFoundException("User not found");
                });

        tokenRepository.deleteByUserID(user.getUserID());

        String token = generateSecureToken();
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setToken(token);
        resetToken.setUser(user);
        resetToken.setExpiryDate(LocalDateTime.now().plusHours(RESET_PASSWORD_EXPIRY_HOURS));
        tokenRepository.saveAndFlush(resetToken);
        log.info("New token generated and saved to database for userID: {}", user.getUserID());

        // Send to frontend route instead of backend API
        String resetLink = appUrl + VERIFY_EMAIL_ENDPOINT + token;

        emailProducerService.sendPasswordResetEmail(email, resetLink);

    }

    public void resetPassword(String token, String newPassword) {

        PasswordResetToken resetToken = tokenRepository.getTokenEntityByToken(token)
                .orElseThrow(() -> {
                    log.error("Invalid or expired token");
                    return new InvalidTokenException("Invalid or expired token");
                });

        if (resetToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            log.error("Token has expired");
            throw new InvalidTokenException("Token has expired");
        }

        if (resetToken.isUsed()) {
            log.error("Token has already been used");
            throw new InvalidTokenException("Token has already been used");
        }

        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        resetToken.setUsed(true);
        tokenRepository.save(resetToken);

    }

    private String generateSecureToken() {
        return UUID.randomUUID() + UUID.randomUUID().toString().replace("-", "");
    }

    @Transactional
    public void cleanupExpiredTokens() {
        try {
            LocalDateTime now = LocalDateTime.now();
            int deletedCount = tokenRepository.deleteByExpiryDateBefore(now);

            log.info("Number of expired tokens deleted: {}", deletedCount);
        } catch (Exception ex) {
            log.error("Exception in deleting expired tokens: ", ex);
            throw new DatabaseDeletionException("Exception in deleting expired tokens");
        }

    }
}
