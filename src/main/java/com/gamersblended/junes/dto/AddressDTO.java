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
public class AddressDTO {

    private String country;
    private String fullName;
    private String streetAddress1;
    private String streetAddress2;
    private String city;
    private String region;
    private String zipCode;
    private String phoneNumber;
    private Boolean isDefault;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
