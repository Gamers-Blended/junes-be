package com.gamersblended.junes.util;

import com.gamersblended.junes.service.CartService;
import com.gamersblended.junes.service.EmailVerificationTokenService;
import com.gamersblended.junes.service.PasswordResetService;
import com.gamersblended.junes.service.WishlistService;
import com.gamersblended.junes.service.order.OrderExpiryService;
import com.gamersblended.junes.service.order.OrderShipmentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class HouseKeepTasksTest {

    @Mock
    private PasswordResetService passwordResetService;

    @Mock
    private EmailVerificationTokenService emailVerificationTokenService;

    @Mock
    private OrderExpiryService orderExpiryService;

    @Mock
    private OrderShipmentService orderShipmentService;

    @Mock
    private CartService cartService;

    @Mock
    private WishlistService wishlistService;

    @InjectMocks
    private HouseKeepTasks houseKeepTasks;

    @Test
    void scheduledHouseKeepExpiredTokens_delegatesToPasswordResetServiceOnly() {
        houseKeepTasks.scheduledHouseKeepExpiredTokens();

        verify(passwordResetService).cleanupExpiredTokens();
        verifyNoInteractions(emailVerificationTokenService, orderExpiryService, orderShipmentService,
                cartService, wishlistService);
    }

    @Test
    void scheduledHouseKeepUnverifiedEmails_delegatesToEmailVerificationTokenServiceOnly() {
        houseKeepTasks.scheduledHouseKeepUnverifiedEmails();

        verify(emailVerificationTokenService).cleanupUnverifiedEmails();
        verifyNoInteractions(passwordResetService, orderExpiryService, orderShipmentService,
                cartService, wishlistService);
    }

    @Test
    void scheduledReleaseExpiredReservations_delegatesToOrderExpiryServiceOnly() {
        houseKeepTasks.scheduledReleaseExpiredReservations();

        verify(orderExpiryService).releaseExpiredReservations();
        verifyNoInteractions(passwordResetService, emailVerificationTokenService, orderShipmentService,
                cartService, wishlistService);
    }

    @Test
    void scheduledSimulateShipment_delegatesToOrderShipmentServiceOnly() {
        houseKeepTasks.scheduledSimulateShipment();

        verify(orderShipmentService).simulateShipment();
        verifyNoInteractions(passwordResetService, emailVerificationTokenService, orderExpiryService,
                cartService, wishlistService);
    }

    @Test
    void scheduledHouseKeepInactiveCarts_delegatesToCartServiceOnly() {
        houseKeepTasks.scheduledHouseKeepInactiveCarts();

        verify(cartService).cleanupInactiveCarts();
        verifyNoInteractions(passwordResetService, emailVerificationTokenService, orderExpiryService,
                orderShipmentService, wishlistService);
    }

    @Test
    void scheduledHouseKeepInactiveWishlists_delegatesToWishlistServiceOnly() {
        houseKeepTasks.scheduledHouseKeepInactiveWishlists();

        verify(wishlistService).cleanupInactiveWishlists();
        verifyNoInteractions(passwordResetService, emailVerificationTokenService, orderExpiryService,
                orderShipmentService, cartService);
    }
}
