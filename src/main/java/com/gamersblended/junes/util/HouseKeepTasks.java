package com.gamersblended.junes.util;

import com.gamersblended.junes.service.CartService;
import com.gamersblended.junes.service.EmailVerificationTokenService;
import com.gamersblended.junes.service.PasswordResetService;
import com.gamersblended.junes.service.WishlistService;
import com.gamersblended.junes.service.order.OrderExpiryService;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class HouseKeepTasks {

    private final PasswordResetService passwordResetService;
    private final EmailVerificationTokenService emailVerificationTokenService;
    private final OrderExpiryService orderExpiryService;
    private final CartService cartService;
    private final WishlistService wishlistService;

    public HouseKeepTasks(PasswordResetService passwordResetService, EmailVerificationTokenService emailVerificationTokenService, OrderExpiryService orderExpiryService, CartService cartService, WishlistService wishlistService) {
        this.passwordResetService = passwordResetService;
        this.emailVerificationTokenService = emailVerificationTokenService;
        this.orderExpiryService = orderExpiryService;
        this.cartService = cartService;
        this.wishlistService = wishlistService;
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

    @Scheduled(cron = "${housekeeping.reservation-expiry.cron: 0 */5 * * * *}")
    @SchedulerLock(name = "ReservationExpiryTask", lockAtMostFor = "${housekeeping.reservation-expiry.lock-at-most}", lockAtLeastFor = "${housekeeping.reservation-expiry.lock-at-least}")
    public void scheduledReleaseExpiredReservations() {
        log.info("Starting scheduled house keeping for expired inventory reservations...");
        orderExpiryService.releaseExpiredReservations();
    }

    @Scheduled(cron = "${housekeeping.cart-cleanup.cron: 0 0 2 * * *}")
    @SchedulerLock(name = "CartCleanupTask", lockAtMostFor = "${housekeeping.cart-cleanup.lock-at-most}", lockAtLeastFor = "${housekeeping.cart-cleanup.lock-at-least}")
    public void scheduledHouseKeepInactiveCarts() {
        log.info("Starting scheduled house keeping for inactive carts...");
        cartService.cleanupInactiveCarts();
    }

    @Scheduled(cron = "${housekeeping.wishlist-cleanup.cron: 0 0 3 * * *}")
    @SchedulerLock(name = "WishlistCleanupTask", lockAtMostFor = "${housekeeping.wishlist-cleanup.lock-at-most}", lockAtLeastFor = "${housekeeping.wishlist-cleanup.lock-at-least}")
    public void scheduledHouseKeepInactiveWishlists() {
        log.info("Starting scheduled house keeping for inactive wishlists...");
        wishlistService.cleanupInactiveWishlists();
    }
}
