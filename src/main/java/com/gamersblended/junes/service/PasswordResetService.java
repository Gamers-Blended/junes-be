package com.gamersblended.junes.service;

import com.gamersblended.junes.exception.UserNotFoundException;
import com.gamersblended.junes.model.PasswordResetToken;
import com.gamersblended.junes.model.User;
import com.gamersblended.junes.repository.jpa.PasswordResetTokenRepository;
import com.gamersblended.junes.repository.jpa.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static com.gamersblended.junes.constant.ConfigSettingsConstants.EXPIRY_HOURS;

@Slf4j
@Service
public class PasswordResetService {

    @Value("${baseURL:}")
    private String baseURL;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final EmailProducerService emailProducerService;

    public PasswordResetService(UserRepository userRepository, PasswordResetTokenRepository tokenRepository,
                                EmailProducerService emailProducerService) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.emailProducerService = emailProducerService;
    }

    @Transactional
    public String initiatePasswordReset(String email) {

        User user = userRepository.getUserByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        tokenRepository.deleteByUserID(user.getUserID());

        String token = generateSecureToken();
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setToken(token);
        resetToken.setUser(user);
        resetToken.setExpiryDate(LocalDateTime.now().plusHours(EXPIRY_HOURS));
        tokenRepository.saveAndFlush(resetToken);

        String resetLink = baseURL + "junes/api/v1/user/reset-password?token=" + token;

        emailProducerService.sendPasswordResetEmail(email, resetLink);

        return "If the email exists, a reset link has been sent";
    }

    private String generateSecureToken() {
        return UUID.randomUUID() + UUID.randomUUID().toString().replace("-", "");
    }
}
