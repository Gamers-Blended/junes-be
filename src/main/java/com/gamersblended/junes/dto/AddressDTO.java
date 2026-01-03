package com.gamersblended.junes.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AddressDTO {

    private String fullName;
    private String addressLine;
    private String unitNumber;
    private String country;
    private String zipCode;
    private String phoneNumber;
    private Boolean isDefault;
}
