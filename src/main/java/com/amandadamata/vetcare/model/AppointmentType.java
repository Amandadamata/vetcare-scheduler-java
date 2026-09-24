package com.amandadamata.vetcare.model;

public enum AppointmentType {
    CONSULTATION("Consultation"),
    VACCINATION("Vaccination"),
    FOLLOW_UP("Follow-up"),
    EXAM("Exam");

    private final String displayName;

    AppointmentType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

