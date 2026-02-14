package com.gamersblended.junes.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AddressDTO {

    private UUID addressID;
    private String fullName;
    private String addressLine;
    private String unitNumber;
    private String country;
    private String zipCode;
    private String phoneNumber;
    private Boolean isDefault;
}
