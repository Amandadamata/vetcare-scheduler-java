package com.amandadamata.vetcare.model;

public enum PatientCondition {
    STABLE("Stable"),
    CONCERNING("Concerning"),
    CRITICAL("Critical");

    private final String displayName;

    PatientCondition(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

