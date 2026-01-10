package com.apex.backend.model;

import java.time.LocalDate;

public record CorporateAction(LocalDate actionDate, Type type, double ratio) {
    public enum Type {
        SPLIT,
        DIVIDEND
    }
}
