package com.gamersblended.junes.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateUserRequest {
    @NotBlank @Email
    private String email;

    @NotBlank @Size(min = 6, max = 50)
    private String password;
}
