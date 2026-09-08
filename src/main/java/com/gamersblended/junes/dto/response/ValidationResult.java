package com.gamersblended.junes.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResult {
    private String field;
    private boolean valid;
    private List<String> errorList;

    public String getErrorMessage() {
        return String.join(", ", errorList);
    }
}
