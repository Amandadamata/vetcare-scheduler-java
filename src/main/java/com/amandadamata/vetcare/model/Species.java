package com.amandadamata.vetcare.model;

public enum Species {
    DOG("Dog"),
    CAT("Cat"),
    BIRD("Bird"),
    OTHER("Other");

    private final String displayName;

    Species(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

