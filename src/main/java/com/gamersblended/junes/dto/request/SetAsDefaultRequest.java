package com.gamersblended.junes.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SetAsDefaultRequest {

    private String mode;
    private UUID savedItemID;
}
