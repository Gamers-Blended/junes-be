package com.gamersblended.junes.dto;

import com.gamersblended.junes.constant.ValidationConstants;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = ValidationConstants.PASSWORD_MIN_LENGTH, max = ValidationConstants.PASSWORD_MAX_LENGTH,
            message = "Password must be between " + ValidationConstants.PASSWORD_MIN_LENGTH + " and "
                    + ValidationConstants.PASSWORD_MAX_LENGTH + "characters")
    private String password;
}
