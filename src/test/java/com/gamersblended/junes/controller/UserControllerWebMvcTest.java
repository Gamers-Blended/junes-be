package com.gamersblended.junes.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamersblended.junes.WebMvcTestApplication;
import com.gamersblended.junes.dto.request.UpdateEmailRequest;
import com.gamersblended.junes.dto.request.UpdatePasswordRequest;
import com.gamersblended.junes.dto.response.UserDetailsResponse;
import com.gamersblended.junes.service.auth.AccessTokenService;
import com.gamersblended.junes.service.auth.UserService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code @WebMvcTest} + MockMvc slice test for {@link UserController}, following the pattern
 * established in {@link AuthControllerWebMvcTest}. Exercises real JSON (de)serialization and real
 * {@code @Valid} request-body validation on {@link UpdateEmailRequest}/{@link UpdatePasswordRequest}
 * over a simulated HTTP request.
 * <p>
 * Security filters are bypassed via {@code addFilters = false}; {@code /junes/api/v1/user/**} currently
 * falls under the temporary catch-all {@code permitAll()} in
 * {@link com.gamersblended.junes.config.SecurityConfig}.
 */
@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = WebMvcTestApplication.class)
class UserControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @MockBean
    private AccessTokenService accessTokenService;

    // JwtAuthenticationFilter is a @Component Filter, so @WebMvcTest instantiates it even with
    // addFilters = false (that flag only skips running filters, not building the bean); it needs
    // a JwtUtils bean to construct, which this slice otherwise wouldn't provide.
    @MockBean
    private JwtUtils jwtUtils;

    private static UpdateEmailRequest updateEmailRequest(String currentEmail, String newEmail) {
        UpdateEmailRequest request = new UpdateEmailRequest();
        request.setCurrentEmail(currentEmail);
        request.setNewEmail(newEmail);
        return request;
    }

    private static UpdatePasswordRequest updatePasswordRequest(String currentPassword, String newPassword) {
        UpdatePasswordRequest request = new UpdatePasswordRequest();
        request.setCurrentPassword(currentPassword);
        request.setNewPassword(newPassword);
        return request;
    }

    @Test
    void getUserDetails_shouldReturnUserDetails() throws Exception {
        String authHeader = "Bearer token123";
        UUID userID = UUID.randomUUID();
        UserDetailsResponse userDetailsResponse = new UserDetailsResponse("user@example.com");

        when(accessTokenService.extractUserIDFromToken(authHeader)).thenReturn(userID);
        when(userService.getUserDetails(userID)).thenReturn(userDetailsResponse);

        mockMvc.perform(get("/junes/api/v1/user/details").header("Authorization", authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user@example.com"));

        verify(userService).getUserDetails(userID);
    }

    @Test
    void updateEmail_shouldTriggerUpdateAndReturnConfirmation() throws Exception {
        String authHeader = "Bearer token123";
        UUID userID = UUID.randomUUID();
        UpdateEmailRequest request = updateEmailRequest("old@example.com", "new@example.com");

        when(accessTokenService.extractUserIDFromToken(authHeader)).thenReturn(userID);

        mockMvc.perform(patch("/junes/api/v1/user/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", authHeader)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Updating of email triggered. Please check your inbox for verification email"));

        verify(userService).updateEmail(eq(userID), any(UpdateEmailRequest.class));
    }

    @Test
    void updateEmail_blankCurrentEmail_returnsBadRequestFromBeanValidation() throws Exception {
        UpdateEmailRequest request = updateEmailRequest("", "new@example.com");

        mockMvc.perform(patch("/junes/api/v1/user/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer token123")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Email is required")));
    }

    @Test
    void updateEmail_invalidNewEmailFormat_returnsBadRequestFromBeanValidation() throws Exception {
        UpdateEmailRequest request = updateEmailRequest("old@example.com", "not-an-email");

        mockMvc.perform(patch("/junes/api/v1/user/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer token123")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Invalid email format")));
    }

    @Test
    void updatePassword_shouldTriggerUpdateAndReturnConfirmation() throws Exception {
        String authHeader = "Bearer token123";
        UUID userID = UUID.randomUUID();
        UpdatePasswordRequest request = updatePasswordRequest("oldPassword123", "newPassword123");

        when(accessTokenService.extractUserIDFromToken(authHeader)).thenReturn(userID);

        mockMvc.perform(patch("/junes/api/v1/user/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", authHeader)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password successfully updated"));

        verify(userService).updatePassword(eq(userID), any(UpdatePasswordRequest.class), any());
    }

    @Test
    void updatePassword_blankCurrentPassword_returnsBadRequestFromBeanValidation() throws Exception {
        UpdatePasswordRequest request = updatePasswordRequest("", "newPassword123");

        mockMvc.perform(patch("/junes/api/v1/user/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer token123")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Password is required")));
    }

    @Test
    void updatePassword_newPasswordTooShort_returnsBadRequestFromBeanValidation() throws Exception {
        UpdatePasswordRequest request = updatePasswordRequest("oldPassword123", "abc");

        mockMvc.perform(patch("/junes/api/v1/user/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer token123")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Password must be between")));
    }
}
