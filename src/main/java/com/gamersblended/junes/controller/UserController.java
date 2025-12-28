package com.gamersblended.junes.controller;

import com.gamersblended.junes.annotation.RateLimit;
import com.gamersblended.junes.dto.reponse.ResponseMessage;
import com.gamersblended.junes.dto.reponse.UserDetailsResponse;
import com.gamersblended.junes.exception.UserNotFoundException;
import com.gamersblended.junes.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("junes/api/v1/user")
@RateLimit(requests = 10, duration = 1, timeUnit = TimeUnit.MINUTES)
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
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
}
