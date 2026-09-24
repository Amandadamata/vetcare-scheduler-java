package com.amandadamata.vetcare.model;

import java.time.LocalDate;
import java.util.UUID;

public class Animal {
    private UUID id;
    private UUID ownerId;
    private String name;
    private Species species;
    private LocalDate birthDate;
    private String notes;

    @SuppressWarnings("unused")
    private Animal() {
        // Required by Gson.
    }

    public Animal(UUID id, UUID ownerId, String name, Species species, LocalDate birthDate, String notes) {
        this.id = id;
        this.ownerId = ownerId;
        this.name = name;
        this.species = species;
        this.birthDate = birthDate;
        this.notes = notes;
    }

    public void update(String name, Species species, LocalDate birthDate, String notes) {
        this.name = name;
        this.species = species;
        this.birthDate = birthDate;
        this.notes = notes;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getName() {
        return name;
    }

    public Species getSpecies() {
        return species;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public String getNotes() {
        return notes;
    }
}

