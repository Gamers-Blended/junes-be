package com.gamersblended.junes.dto.request;

import com.gamersblended.junes.constant.ValidationConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PasswordResetRequest {

    @NotBlank(message = "Token is required")
    private String token;

    @NotBlank(message = "Password is required")
    @Size(min = ValidationConstants.PASSWORD_MIN_LENGTH, max = ValidationConstants.PASSWORD_MAX_LENGTH,
            message = "Password must be between " + ValidationConstants.PASSWORD_MIN_LENGTH + " and "
                    + ValidationConstants.PASSWORD_MAX_LENGTH + "characters")
    private String newPassword;
}
