package com.amandadamata.vetcare.model;

public enum AppointmentStatus {
    SCHEDULED("Scheduled"),
    CANCELLED("Cancelled");

    private final String displayName;

    AppointmentStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

