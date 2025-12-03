package com.gamersblended.junes.util;

import java.util.ArrayList;
import java.util.List;

public class ValidationResult {
    private final String field;
    private final boolean valid;
    private final List<String> errorList;

    public ValidationResult(String field, boolean valid, List<String> errorList) {
        this.field = field;
        this.valid = valid;
        this.errorList = errorList;
    }

    public boolean isValid() {
        return valid;
    }

    public List<String> getErrorList() {
        return new ArrayList<>(errorList);
    }

    public String getErrorMessage() {
        return String.join(", ", errorList);
    }
}
