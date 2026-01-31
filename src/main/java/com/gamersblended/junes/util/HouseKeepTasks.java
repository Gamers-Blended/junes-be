package com.gamersblended.junes.util;

import com.gamersblended.junes.service.EmailVerificationTokenService;
import com.gamersblended.junes.service.PasswordResetService;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class HouseKeepTasks {

    private final PasswordResetService passwordResetService;
    private final EmailVerificationTokenService emailVerificationTokenService;

    public HouseKeepTasks(PasswordResetService passwordResetService, EmailVerificationTokenService emailVerificationTokenService) {
        this.passwordResetService = passwordResetService;
        this.emailVerificationTokenService = emailVerificationTokenService;
    }

    @Scheduled(cron = "${housekeeping.token-cleanup.cron: 0 0 */12 * * *}")
    @SchedulerLock(name = "TokenCleanupTask", lockAtMostFor = "${housekeeping.token-cleanup.lock-at-most}", lockAtLeastFor = "${housekeeping.token-cleanup.lock-at-least}")
    public void scheduledHouseKeepExpiredTokens() {
        log.info("Starting scheduled house keeping for blacklisted tokens...");
        passwordResetService.cleanupExpiredTokens();
    }

    @Scheduled(cron = "${housekeeping.unverified-email-cleanup.cron: 0 0 */12 * * *}")
    @SchedulerLock(name = "UnverifiedEmailCleanupTask", lockAtMostFor = "${housekeeping.unverified-email-cleanup.lock-at-most}", lockAtLeastFor = "${housekeeping.unverified-email-cleanup.lock-at-least}")
    public void scheduledHouseKeepUnverifiedEmails() {
        log.info("Starting scheduled house keeping for unverified emails...");
        emailVerificationTokenService.cleanupUnverifiedEmails();
    }
}
