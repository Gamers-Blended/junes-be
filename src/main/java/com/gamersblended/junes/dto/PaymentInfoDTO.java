package com.gamersblended.junes.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PaymentInfoDTO {

    private String paymentMethodId;
    private String last4Digits; // Only last 4 digits for display
    private String cardBrand;
    private String expirationMonth; // MM
    private String expirationYear; // YYYY
    private String nameOnCard;
    private Boolean isDefault;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
