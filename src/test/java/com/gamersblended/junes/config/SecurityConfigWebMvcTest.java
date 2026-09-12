package com.gamersblended.junes.config;

import com.gamersblended.junes.WebMvcTestApplication;
import com.gamersblended.junes.util.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the route authorization rules declared in {@link SecurityConfig#securityFilterChain}
 * against real MockMvc requests (filters enabled), using {@link SecurityConfigTestController} as a
 * stand-in mapped at each differently-authorised prefix.
 */
@WebMvcTest(controllers = SecurityConfigTestController.class)
@Import(SecurityConfig.class)
@ContextConfiguration(classes = WebMvcTestApplication.class)
class SecurityConfigWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    // JwtAuthenticationFilter is a @Component picked up by this slice's component scan and needs a
    // JwtUtils bean to construct, even though these tests don't exercise JWT parsing directly.
    @MockBean
    private JwtUtils jwtUtils;

    @Test
    void frontpage_isPubliclyAccessible() throws Exception {
        mockMvc.perform(get("/junes/api/v1/frontpage/probe")).andExpect(status().isOk());
    }

    @Test
    void product_isPubliclyAccessible() throws Exception {
        mockMvc.perform(get("/junes/api/v1/product/probe")).andExpect(status().isOk());
    }

    @Test
    void cart_isPubliclyAccessible() throws Exception {
        mockMvc.perform(get("/junes/api/v1/cart/probe")).andExpect(status().isOk());
    }

    @Test
    void wishlist_isPubliclyAccessible() throws Exception {
        mockMvc.perform(get("/junes/api/v1/wishlist/probe")).andExpect(status().isOk());
    }

    @Test
    void auth_isPubliclyAccessible() throws Exception {
        mockMvc.perform(get("/junes/api/v1/auth/probe")).andExpect(status().isOk());
    }

    @Test
    void unlistedRouteUnderApiPrefix_isPubliclyAccessible_viaCatchAllTodo() throws Exception {
        mockMvc.perform(get("/junes/api/v1/some-other-route/probe")).andExpect(status().isOk());
    }

    @Test
    void housekeep_withoutAuthentication_isUnauthorized() throws Exception {
        mockMvc.perform(get("/junes/api/v1/housekeep/probe")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void housekeep_withNonAdminRole_isForbidden() throws Exception {
        mockMvc.perform(get("/junes/api/v1/housekeep/probe")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void housekeep_withAdminRole_isAllowed() throws Exception {
        mockMvc.perform(get("/junes/api/v1/housekeep/probe")).andExpect(status().isOk());
    }

    @Test
    void routeOutsideApiPrefix_withoutAuthentication_isUnauthorized() throws Exception {
        mockMvc.perform(get("/outside/probe")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void routeOutsideApiPrefix_withAuthentication_isAllowed() throws Exception {
        mockMvc.perform(get("/outside/probe")).andExpect(status().isOk());
    }
}
