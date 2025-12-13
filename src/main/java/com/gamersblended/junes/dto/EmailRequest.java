package com.gamersblended.junes.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@Setter
public class EmailRequest {
    private String to;
    private String subject;
    private String body;
}
