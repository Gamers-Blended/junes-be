package com.gamersblended.junes.controller;

import com.gamersblended.junes.WebMvcTestApplication;
import com.gamersblended.junes.service.auth.EmailVerificationTokenService;
import com.gamersblended.junes.service.auth.PasswordResetService;
import com.gamersblended.junes.service.cart.CartService;
import com.gamersblended.junes.service.cart.WishlistService;
import com.gamersblended.junes.service.order.OrderShipmentService;
import com.gamersblended.junes.util.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code @WebMvcTest} + MockMvc slice test for {@link HouseKeepController}, following the pattern
 * established in {@link AuthControllerWebMvcTest}.
 * <p>
 * {@code /junes/api/v1/housekeep/**} requires {@code hasRole("ADMIN")} in
 * {@link com.gamersblended.junes.config.SecurityConfig}, but that restriction is enforced purely by
 * the {@code SecurityFilterChain} — there is no method-level {@code @PreAuthorize}/{@code @Secured} on
 * this controller. Since {@code addFilters = false} bypasses the filter chain entirely, the ADMIN gate
 * cannot be exercised at this slice level (the same limitation the plain-Mockito test it replaces had,
 * since a direct method call bypasses the servlet layer too) — this class only covers delegation
 * behaviour, not authorization.
 */
@WebMvcTest(HouseKeepController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = WebMvcTestApplication.class)
class HouseKeepControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PasswordResetService passwordResetService;

    @MockBean
    private EmailVerificationTokenService emailVerificationTokenService;

    @MockBean
    private CartService cartService;

    @MockBean
    private WishlistService wishlistService;

    @MockBean
    private OrderShipmentService orderShipmentService;

    // JwtAuthenticationFilter is a @Component Filter, so @WebMvcTest instantiates it even with
    // addFilters = false (that flag only skips running filters, not building the bean); it needs
    // a JwtUtils bean to construct, which this slice otherwise wouldn't provide.
    @MockBean
    private JwtUtils jwtUtils;

    @Test
    void houseKeepExpiredTokens_shouldTriggerCleanupAndReturnOk() throws Exception {
        mockMvc.perform(post("/junes/api/v1/housekeep/tokens"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Blacklisted tokens cleared"));

        verify(passwordResetService).cleanupExpiredTokens();
        verifyNoInteractions(emailVerificationTokenService, cartService, wishlistService, orderShipmentService);
    }

    @Test
    void houseKeepUnverifiedEmails_shouldTriggerCleanupAndReturnOk() throws Exception {
        mockMvc.perform(post("/junes/api/v1/housekeep/unverified-emails"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Unverified emails cleared"));

        verify(emailVerificationTokenService).cleanupUnverifiedEmails();
        verifyNoInteractions(passwordResetService, cartService, wishlistService, orderShipmentService);
    }

    @Test
    void houseKeepInactiveCarts_shouldTriggerCleanupAndReturnOk() throws Exception {
        mockMvc.perform(post("/junes/api/v1/housekeep/carts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Inactive carts cleared"));

        verify(cartService).cleanupInactiveCarts();
        verifyNoInteractions(passwordResetService, emailVerificationTokenService, wishlistService, orderShipmentService);
    }

    @Test
    void houseKeepInactiveWishlists_shouldTriggerCleanupAndReturnOk() throws Exception {
        mockMvc.perform(post("/junes/api/v1/housekeep/wishlists"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Inactive wishlists cleared"));

        verify(wishlistService).cleanupInactiveWishlists();
        verifyNoInteractions(passwordResetService, emailVerificationTokenService, cartService, orderShipmentService);
    }

    @Test
    void houseKeepSimulateShipment_shouldTriggerSimulationAndReturnOk() throws Exception {
        mockMvc.perform(post("/junes/api/v1/housekeep/shipments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Awaiting-shipment orders transitioned to Shipped"));

        verify(orderShipmentService).simulateShipment();
        verifyNoInteractions(passwordResetService, emailVerificationTokenService, cartService, wishlistService);
    }
}
