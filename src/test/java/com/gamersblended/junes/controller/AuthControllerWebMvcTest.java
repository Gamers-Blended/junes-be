package com.gamersblended.junes.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamersblended.junes.WebMvcTestApplication;
import com.gamersblended.junes.dto.request.CreateUserRequest;
import com.gamersblended.junes.dto.request.LoginRequest;
import com.gamersblended.junes.dto.request.PasswordResetRequest;
import com.gamersblended.junes.dto.response.LoginResponse;
import com.gamersblended.junes.dto.response.LogoutResponse;
import com.gamersblended.junes.exception.*;
import com.gamersblended.junes.service.auth.AuthService;
import com.gamersblended.junes.service.auth.PasswordResetService;
import com.gamersblended.junes.util.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test using the @WebMvcTest + MockMvc pattern used by every controller test in this package.
 * Unlike a plain-Mockito, direct-method-call test, this exercises real JSON (de)serialization, real
 * {@code @Valid} request-body validation (including its mapping to a 400 via
 * {@link com.gamersblended.junes.config.GlobalExceptionHandler#handleMethodArgumentNotValidException}),
 * and real exception-to-HTTP-status mapping over a simulated HTTP request/response — none of which a
 * direct method-call test can reach.
 * <p>
 * Security filters are bypassed via {@code addFilters = false} since {@code /junes/api/v1/auth/**} is
 * permitAll in {@link com.gamersblended.junes.config.SecurityConfig} and this class targets
 * request/validation/exception-mapping behaviour, not authentication.
 * <p>
 * Uses {@link WebMvcTestApplication} as the context source instead of the real
 * {@link com.gamersblended.junes.JunesApplication} — see its Javadoc for why.
 */
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = WebMvcTestApplication.class)
class AuthControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private PasswordResetService passwordResetService;

    // JwtAuthenticationFilter is a @Component Filter, so @WebMvcTest instantiates it even with
    // addFilters = false (that flag only skips running filters, not building the bean); it needs
    // a JwtUtils bean to construct, which this slice otherwise wouldn't provide.
    @MockBean
    private JwtUtils jwtUtils;

    private static CreateUserRequest createUserRequest(String email) {
        CreateUserRequest request = new CreateUserRequest();
        request.setEmail(email);
        request.setPassword("P@ssw0rd!");
        return request;
    }

    private static LoginRequest loginRequest(String email, String password) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }

    private static PasswordResetRequest passwordResetRequest(String token) {
        PasswordResetRequest request = new PasswordResetRequest();
        request.setToken(token);
        request.setNewPassword("newP@ssw0rd!");
        return request;
    }

    @Test
    void addUser_validBody_returnsOkAndDelegatesToService() throws Exception {
        CreateUserRequest request = createUserRequest("player@example.com");

        mockMvc.perform(post("/junes/api/v1/auth/add-user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User added with unverified email"));

        verify(authService).addUser("player@example.com", "P@ssw0rd!");
    }

    @Test
    void addUser_blankEmail_returnsBadRequestFromBeanValidation() throws Exception {
        CreateUserRequest request = createUserRequest("");

        mockMvc.perform(post("/junes/api/v1/auth/add-user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Email is required")));
    }

    @Test
    void addUser_invalidEmailFormat_returnsBadRequestFromBeanValidation() throws Exception {
        CreateUserRequest request = createUserRequest("not-an-email");

        mockMvc.perform(post("/junes/api/v1/auth/add-user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Invalid email format")));
    }

    @Test
    void resetPassword_blankToken_returnsBadRequestFromBeanValidation() throws Exception {
        PasswordResetRequest request = passwordResetRequest("");

        mockMvc.perform(post("/junes/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Token is required")));
    }

    @Test
    void resetPassword_validBody_returnsOk() throws Exception {
        PasswordResetRequest request = passwordResetRequest("reset-token");

        mockMvc.perform(post("/junes/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password has been reset successfully"));

        verify(passwordResetService).resetPassword("reset-token", "newP@ssw0rd!");
    }

    @Test
    void login_validCredentials_returnsOkWithLoginResponse() throws Exception {
        UUID userID = UUID.randomUUID();
        LoginRequest request = loginRequest("player@example.com", "P@ssw0rd!");
        when(authService.login(any(LoginRequest.class), any())).thenReturn(
                new LoginResponse("jwt-token", "Bearer", userID, "player@example.com"));

        mockMvc.perform(post("/junes/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.userID").value(userID.toString()));
    }

    @Test
    void login_userNotFound_returns404WithStandardErrorBody() throws Exception {
        LoginRequest request = loginRequest("missing@example.com", "P@ssw0rd!");
        when(authService.login(any(LoginRequest.class), any()))
                .thenThrow(new UserNotFoundException("User with given email not found"));

        mockMvc.perform(post("/junes/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("User with given email not found"));
    }

    @Test
    void login_invalidPassword_returns400ViaInputValidationException() throws Exception {
        LoginRequest request = loginRequest("player@example.com", "wrong-password");
        when(authService.login(any(LoginRequest.class), any()))
                .thenThrow(new InputValidationException("Invalid email or password"));

        mockMvc.perform(post("/junes/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_disabledAccount_returns403ViaUserDisabledException() throws Exception {
        LoginRequest request = loginRequest("player@example.com", "P@ssw0rd!");
        when(authService.login(any(LoginRequest.class), any()))
                .thenThrow(new UserDisabledException("Account is disabled"));

        mockMvc.perform(post("/junes/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void login_unverifiedEmail_returns403ViaUserNotVerifiedException() throws Exception {
        LoginRequest request = loginRequest("player@example.com", "P@ssw0rd!");
        when(authService.login(any(LoginRequest.class), any()))
                .thenThrow(new UserNotVerifiedException("User's email is not verified"));

        mockMvc.perform(post("/junes/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void resendVerificationEmail_alreadyVerified_returns409ViaEmailAlreadyVerifiedException() throws Exception {
        doThrow(new EmailAlreadyVerifiedException("player@example.com is already verified"))
                .when(authService).resendVerificationEmail("player@example.com");

        mockMvc.perform(post("/junes/api/v1/auth/resend-verification")
                        .param("email", "player@example.com"))
                .andExpect(status().isConflict());
    }

    @Test
    void verifyEmail_invalidToken_returns401ViaInvalidTokenException() throws Exception {
        doThrow(new InvalidTokenException("Unsupported token purpose: RESET"))
                .when(authService).verifyEmail("bad-token");

        mockMvc.perform(get("/junes/api/v1/auth/verify").param("token", "bad-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_missingToken_returns401ViaMissingTokenException() throws Exception {
        when(authService.logout("")).thenThrow(new MissingTokenException("No token provided for logout"));

        mockMvc.perform(post("/junes/api/v1/auth/logout").header("Authorization", ""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_success_returnsOkWithLogoutResponse() throws Exception {
        when(authService.logout("Bearer valid-token"))
                .thenReturn(new LogoutResponse("Logged out successfully", null));

        mockMvc.perform(post("/junes/api/v1/auth/logout").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully"));
    }
}
