package org.healthily.demo.model;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ResponseType {
    YES,
    NO;

    public boolean toBooleanValue() {
        return this == YES;
    }

    @JsonCreator
    public static ResponseType fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Response cannot be null");
        }
        return switch (value.toLowerCase()) {
            case "yes" -> YES;
            case "no" -> NO;
            default -> throw new IllegalArgumentException("Invalid response: " + value + ". Must be 'yes' or 'no'");
        };
    }
} 