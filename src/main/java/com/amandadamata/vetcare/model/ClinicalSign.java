package com.amandadamata.vetcare.model;

public enum ClinicalSign {
    BREATHING_DIFFICULTY("Breathing difficulty"),
    UNCONTROLLED_BLEEDING("Uncontrolled bleeding"),
    UNCONSCIOUSNESS("Unconsciousness"),
    SEIZURE("Seizure"),
    SEVERE_PAIN("Severe pain"),
    REPEATED_VOMITING("Repeated vomiting"),
    UNABLE_TO_STAND("Unable to stand"),
    OTHER("Other or no listed sign");

    private final String displayName;

    ClinicalSign(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

