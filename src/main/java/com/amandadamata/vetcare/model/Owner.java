package com.amandadamata.vetcare.model;

import java.util.UUID;

public class Owner {
    /** Name shown for owners whose personal data was removed. */
    public static final String ANONYMIZED_NAME = "Anonymized owner";

    private UUID id;
    private String name;
    private String phone;
    private String email;
    private boolean anonymized;

    @SuppressWarnings("unused")
    private Owner() {
        // Required by Gson.
    }

    public Owner(UUID id, String name, String phone, String email) {
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.email = email;
    }

    /**
     * A copy of this owner without personal data. The ID is kept so that animals and appointments
     * still refer to an existing owner.
     */
    public Owner anonymizedCopy() {
        Owner copy = new Owner(id, ANONYMIZED_NAME, "", "");
        copy.anonymized = true;
        return copy;
    }

    public void update(String name, String phone, String email) {
        this.name = name;
        this.phone = phone;
        this.email = email;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public boolean isAnonymized() {
        return anonymized;
    }
}

