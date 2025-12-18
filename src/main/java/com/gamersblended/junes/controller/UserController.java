package com.gamersblended.junes.controller;

import com.gamersblended.junes.annotation.RateLimit;
import com.gamersblended.junes.dto.CreateUserRequest;
import com.gamersblended.junes.dto.PasswordResetRequestDTO;
import com.gamersblended.junes.dto.ForgotPasswordRequestDTO;
import com.gamersblended.junes.dto.UsersDTO;
import com.gamersblended.junes.exception.EmailAlreadyVerifiedException;
import com.gamersblended.junes.exception.UserNotFoundException;
import com.gamersblended.junes.service.PasswordResetService;
import com.gamersblended.junes.service.UserService;
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
@RequestMapping("junes/api/v1/user")
@RateLimit(requests = 10, duration = 1, timeUnit = TimeUnit.MINUTES)
public class UserController {

    private final UserService userService;
    private final PasswordResetService passwordResetService;

    public UserController(UserService userService, PasswordResetService passwordResetService) {
        this.userService = userService;
        this.passwordResetService = passwordResetService;
    }

    @Operation(summary = "Adds a new user to database")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User added, but not verified",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = UsersDTO.class))})
    })
    @PostMapping("/add-user")
    public ResponseEntity<String> addUser(@RequestBody CreateUserRequest createUserRequest) {
        userService.addUser(createUserRequest);
        return ResponseEntity.ok("User added with unverified email");
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<String> resendVerificationEmail(@RequestParam String email) {
        try {
            if (userService.resendVerificationEmail(email)) {
                return ResponseEntity.ok("Verification email successfully resent");
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Error in resending verification email");
        } catch (UserNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
        } catch (EmailAlreadyVerifiedException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        }
    }

    @GetMapping("/verify")
    public ResponseEntity<String> verifyEmail(@RequestParam String token) {
        if (userService.verifyEmail(token)) {
            return ResponseEntity.ok("Email verified successfully");
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid or expired verification link");
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@Valid @RequestBody ForgotPasswordRequestDTO resetRequestDTO) {
        try {
            return ResponseEntity.ok(passwordResetService.initiatePasswordReset(resetRequestDTO.getEmail()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Error in sending password reset email");
        }
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
