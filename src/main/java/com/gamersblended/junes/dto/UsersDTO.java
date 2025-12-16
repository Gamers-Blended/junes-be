package com.gamersblended.junes.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class UsersDTO {

    private UUID userID;
    private String passwordHash;
    private String email;
}
