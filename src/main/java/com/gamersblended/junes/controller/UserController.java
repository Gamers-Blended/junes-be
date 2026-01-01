package com.gamersblended.junes.controller;

import com.gamersblended.junes.annotation.RateLimit;
import com.gamersblended.junes.dto.reponse.ResponseMessage;
import com.gamersblended.junes.dto.reponse.UserDetailsResponse;
import com.gamersblended.junes.dto.request.UpdateEmailRequest;
import com.gamersblended.junes.dto.request.UpdatePasswordRequest;
import com.gamersblended.junes.exception.InputValidationException;
import com.gamersblended.junes.exception.InvalidTokenException;
import com.gamersblended.junes.exception.QueueEmailException;
import com.gamersblended.junes.exception.UserNotFoundException;
import com.gamersblended.junes.service.AccessTokenService;
import com.gamersblended.junes.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("junes/api/v1/user")
@RateLimit(requests = 10, duration = 1, timeUnit = TimeUnit.MINUTES)
public class UserController {

    private final UserService userService;
    private final AccessTokenService accessTokenService;

    public UserController(UserService userService, AccessTokenService accessTokenService) {
        this.userService = userService;
        this.accessTokenService = accessTokenService;
    }

    @Operation(summary = "Get user's details from userID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User details successfully retrieved",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ResponseMessage.class))}),
            @ApiResponse(responseCode = "404", description = "User with given ID not found",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = UserNotFoundException.class))})
    })
    @GetMapping("/{userID}/details")
    public ResponseEntity<UserDetailsResponse> getUserDetails(@PathVariable UUID userID) {
        log.info("Retrieving user details for userID: {}...", userID);
        UserDetailsResponse userDetails = userService.getUserDetails(userID);
        return ResponseEntity.ok(userDetails);
    }

    @Operation(summary = "Update user's email")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Verification email to update email successfully sent",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ResponseMessage.class))}),
            @ApiResponse(responseCode = "400", description = "Token is invalid, has expired, or already used",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = InvalidTokenException.class))}),
            @ApiResponse(responseCode = "500", description = "Invalid user ID format in token",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = IllegalArgumentException.class))}),
            @ApiResponse(responseCode = "400", description = "Invalid email(s) and/or userID given",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = InputValidationException.class))}),
            @ApiResponse(responseCode = "404", description = "User with given current email not found",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = UserNotFoundException.class))}),
            @ApiResponse(responseCode = "500", description = "Error in queuing email",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = QueueEmailException.class))})
    })
    @PatchMapping("/email")
    public ResponseEntity<ResponseMessage> updateEmail(@RequestHeader("Authorization") String authHeader, @Valid @RequestBody UpdateEmailRequest updateEmailRequest) {
        UUID userID = accessTokenService.extractUserIDFromToken(authHeader);

        log.info("Triggering update email for userID: {} from {} to {}", userID, updateEmailRequest.getCurrentEmail(), updateEmailRequest.getNewEmail());
        userService.updateEmail(userID, updateEmailRequest);
        return ResponseEntity.ok(new ResponseMessage("Updating of email triggered. Please check your inbox for verification email"));
    }

    @Operation(summary = "Update user's password")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Password successfully updated",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ResponseMessage.class))}),
            @ApiResponse(responseCode = "400", description = "Token is invalid, has expired, or already used",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = InvalidTokenException.class))}),
            @ApiResponse(responseCode = "500", description = "Invalid user ID format in token",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = IllegalArgumentException.class))}),
            @ApiResponse(responseCode = "400", description = "Invalid new password and/or current password given",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = InputValidationException.class))}),
            @ApiResponse(responseCode = "404", description = "Current password does not match user's in database",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = UserNotFoundException.class))}),
            @ApiResponse(responseCode = "500", description = "Error in queuing email",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = QueueEmailException.class))})
    })
    @PatchMapping("/password")
    public ResponseEntity<ResponseMessage> updatePassword(@RequestHeader("Authorization") String authHeader, @Valid @RequestBody UpdatePasswordRequest updatePasswordRequest,
                                                          HttpServletRequest request) {
        UUID userID = accessTokenService.extractUserIDFromToken(authHeader);

        log.info("Triggering update password for userID: {}", userID);
        userService.updatePassword(userID, updatePasswordRequest, request);
        return ResponseEntity.ok(new ResponseMessage("Password successfully updated"));
    }
}
