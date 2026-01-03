package com.gamersblended.junes.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PaymentMethodDTO {

    private String cardType;
    private String cardLastFour;
    private String cardHolderName;
    private String expirationMonth; // MM
    private String expirationYear; // YYYY
    private String nameOnCard;
    private Boolean isDefault;
    private String status = "active";
}
