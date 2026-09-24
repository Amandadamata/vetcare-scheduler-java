package com.amandadamata.vetcare.model;

public enum TriagePriority {
    EMERGENCY(0, "Emergency"),
    URGENT(1, "Urgent"),
    STANDARD(2, "Standard");

    private final int rank;
    private final String displayName;

    TriagePriority(int rank, String displayName) {
        this.rank = rank;
        this.displayName = displayName;
    }

    public int getRank() {
        return rank;
    }

    public String getDisplayName() {
        return displayName;
    }
}

