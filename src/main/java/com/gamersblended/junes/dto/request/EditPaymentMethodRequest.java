package com.gamersblended.junes.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EditPaymentMethodRequest {

    private String cardHolderName;
    private String expirationMonth; // MM
    private String expirationYear; // YYYY
    private UUID billingAddressID;
}
