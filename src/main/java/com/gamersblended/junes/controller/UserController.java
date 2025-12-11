package com.gamersblended.junes.controller;

import com.gamersblended.junes.annotation.RateLimit;
import com.gamersblended.junes.dto.CreateUserRequest;
import com.gamersblended.junes.dto.UsersDTO;
import com.gamersblended.junes.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    @Operation(summary = "Adds a new user to database")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User added, but not verified",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = UsersDTO.class))})
    })
    @PostMapping("/add-user")
    public ResponseEntity<String> addUser(@RequestBody CreateUserRequest createUserRequest) {
        try {
            return ResponseEntity.ok(userService.addUser(createUserRequest));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }

    @GetMapping("/verify")
    public ResponseEntity<String> verifyEmail(@RequestParam String token) {
        if (userService.verifyEmail(token)) {
            return ResponseEntity.ok("Email verified successfully");
        }
        return ResponseEntity.badRequest().body("Invalid or expired verification link");
    }
}
