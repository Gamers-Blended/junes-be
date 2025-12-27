package com.gamersblended.junes.controller;

import com.gamersblended.junes.annotation.RateLimit;
import com.gamersblended.junes.dto.*;
import com.gamersblended.junes.exception.*;
import com.gamersblended.junes.service.AuthService;
import com.gamersblended.junes.service.PasswordResetService;
import com.gamersblended.junes.util.ValidationResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;
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
                            schema = @Schema(implementation = ResponseMessage.class))}),
            @ApiResponse(responseCode = "400", description = "Validation checks fail for email and/or password",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ValidationResult.class))}),
            @ApiResponse(responseCode = "500", description = "Error in sending email",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = EmailDeliveryException.class))})
    })
    @PostMapping("/add-user")
    @RateLimit(requests = 5, duration = 1, timeUnit = TimeUnit.HOURS, keyFromRequestBody = "email")
    public ResponseEntity<ResponseMessage> addUser(@Valid @RequestBody CreateUserRequest createUserRequest) {
        log.info("Adding new user with email: {}", createUserRequest.getEmail());
        authService.addUser(createUserRequest.getEmail(), createUserRequest.getPassword());
        return ResponseEntity.ok(new ResponseMessage("User added with unverified email"));
    }

    @Operation(summary = "Resend verification link to new user's email")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Verification email successfully resent",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ResponseMessage.class))}),
            @ApiResponse(responseCode = "404", description = "User with given email not found",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = UserNotFoundException.class))}),
            @ApiResponse(responseCode = "400", description = "Email is already verified, no need to verify again",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = EmailAlreadyVerifiedException.class))}),
            @ApiResponse(responseCode = "500", description = "Error in sending email",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = EmailDeliveryException.class))})
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
            @ApiResponse(responseCode = "404", description = "User with given email not found",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = UserNotFoundException.class))}),
            @ApiResponse(responseCode = "500", description = "Error in queuing email",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = QueueEmailException.class))})
    })
    @PostMapping("/forgot-password")
    public ResponseEntity<ResponseMessage> forgotPassword(@RequestParam
                                                          @NotBlank(message = "Email is required")
                                                          @Email(message = "Invalid email format")
                                                          String email) {
        log.info("Password reset requested for: {}", email);
        passwordResetService.initiatePasswordReset(email);
        return ResponseEntity.ok(new ResponseMessage("Reset password successfully sent"));
    }

    @Operation(summary = "Reset user's password")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Reset password successfully sent",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ResponseMessage.class))}),
            @ApiResponse(responseCode = "400", description = "Token is invalid, has expired, or already used",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = InvalidTokenException.class))})
    })
    @PostMapping("/reset-password")
    public ResponseEntity<ResponseMessage> resetPassword(@Valid @RequestBody PasswordResetRequest passwordResetRequest) {
        log.info("Triggering password reset...");
        passwordResetService.resetPassword(passwordResetRequest.getToken(), passwordResetRequest.getNewPassword());
        return ResponseEntity.ok(new ResponseMessage("Password has been reset successfully"));
    }

    @Operation(summary = "Login user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User successfully logged in",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = LoginResponse.class))}),
            @ApiResponse(responseCode = "404", description = "User with given email not found",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = UserNotFoundException.class))}),
            @ApiResponse(responseCode = "400", description = "Invalid password given",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = InputValidationException.class))}),
            @ApiResponse(responseCode = "403", description = "User's account is disabled",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = UserDisabledException.class))}),
            @ApiResponse(responseCode = "403", description = "User's email is not verified",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = UserNotVerifiedException.class))})
    })
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        log.info("Triggering login for user with email: {}...", loginRequest.getEmail());
        return ResponseEntity.ok(authService.login(loginRequest));
    }
}
