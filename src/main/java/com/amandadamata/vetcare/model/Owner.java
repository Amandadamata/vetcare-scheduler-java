package com.amandadamata.vetcare.model;

import java.util.UUID;

public class Owner {
    private UUID id;
    private String name;
    private String phone;
    private String email;

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
}

