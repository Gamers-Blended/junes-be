package com.gamersblended.junes.controller;

import com.gamersblended.junes.annotation.RateLimit;
import com.gamersblended.junes.dto.CreateUserRequest;
import com.gamersblended.junes.dto.ForgotPasswordRequest;
import com.gamersblended.junes.dto.PasswordResetRequestDTO;
import com.gamersblended.junes.dto.ResponseMessage;
import com.gamersblended.junes.exception.QueueEmailException;
import com.gamersblended.junes.exception.UserNotFoundException;
import com.gamersblended.junes.service.AuthService;
import com.gamersblended.junes.service.PasswordResetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("junes/api/v1/auth")
@RateLimit(requests = 10, duration = 1, timeUnit = TimeUnit.MINUTES)
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    public AuthController(AuthService authService, PasswordResetService passwordResetService) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
    }

    @Operation(summary = "Adds a new user to database")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User added, but not verified",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ResponseMessage.class))})
    })
    @PostMapping("/add-user")
    @RateLimit(requests = 5, duration = 1, timeUnit = TimeUnit.HOURS, keyFromRequestBody = "email")
    public ResponseEntity<ResponseMessage> addUser(@RequestBody CreateUserRequest createUserRequest) {
        log.info("Adding new user with email: {}", createUserRequest.getEmail());
        authService.addUser(createUserRequest);
        return ResponseEntity.ok(new ResponseMessage("User added with unverified email"));
    }

    @Operation(summary = "Resend verification link to new user's email")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Verification email successfully resent",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ResponseMessage.class))})
    })
    @PostMapping("/resend-verification")
    @RateLimit(requests = 5, duration = 1, timeUnit = TimeUnit.HOURS, keyFromRequestBody = "email")
    public ResponseEntity<ResponseMessage> resendVerificationEmail(@RequestParam String email) {
        log.info("Resending verification email to: {}", email);
        authService.resendVerificationEmail(email);
        return ResponseEntity.ok(new ResponseMessage("Verification email successfully resent"));
    }

    @Operation(summary = "Verify email using token from verification link")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Email verified successfully",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ResponseMessage.class))})
    })
    @GetMapping("/verify")
    public ResponseEntity<ResponseMessage> verifyEmail(@RequestParam String token) {
        log.info("Verifying email from token...");
        authService.verifyEmail(token);
        return ResponseEntity.ok(new ResponseMessage("Email verified successfully"));
    }

    @Operation(summary = "Send reset password email to user's email")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Reset password successfully sent",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ResponseMessage.class))}),
            @ApiResponse(responseCode = "404", description = "User with given email not found", content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = UserNotFoundException.class))}),
            @ApiResponse(responseCode = "500", description = "Error in queuing email", content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = QueueEmailException.class))})
    })
    @PostMapping("/forgot-password")
    public ResponseEntity<ResponseMessage> forgotPassword(@Valid @RequestBody ForgotPasswordRequest forgotPasswordRequest) {
        log.info("Sending reset password email to {}", forgotPasswordRequest.getEmail());
        passwordResetService.initiatePasswordReset(forgotPasswordRequest.getEmail());
        return ResponseEntity.ok(new ResponseMessage("Reset password successfully sent"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@Valid @RequestBody PasswordResetRequestDTO resetDTO) {
        try {
            return ResponseEntity.ok(passwordResetService.resetPassword(resetDTO.getToken(), resetDTO.getNewPassword()));
        } catch (Exception ex) {
            log.error("Exception in resetting password: ", ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Error in resetting password: " + ex.getMessage());
        }
    }
}
