package com.gamersblended.junes.config;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only controller mapped across every route prefix {@link SecurityConfig} authorises differently,
 * plus one path outside {@code /junes/api/v1/**}, so {@link SecurityConfigWebMvcTest} can exercise the
 * real authorization rules without depending on production controllers and their service graphs.
 */
@RestController
class SecurityConfigTestController {

    @GetMapping("/junes/api/v1/frontpage/probe")
    public String frontpage() {
        return "frontpage-ok";
    }

    @GetMapping("/junes/api/v1/product/probe")
    public String product() {
        return "product-ok";
    }

    @GetMapping("/junes/api/v1/cart/probe")
    public String cart() {
        return "cart-ok";
    }

    @GetMapping("/junes/api/v1/wishlist/probe")
    public String wishlist() {
        return "wishlist-ok";
    }

    @GetMapping("/junes/api/v1/auth/probe")
    public String auth() {
        return "auth-ok";
    }

    @GetMapping("/junes/api/v1/housekeep/probe")
    public String housekeep() {
        return "housekeep-ok";
    }

    @GetMapping("/junes/api/v1/some-other-route/probe")
    public String catchAll() {
        return "catch-all-ok";
    }

    @GetMapping("/outside/probe")
    public String outside() {
        return "outside-ok";
    }
}
