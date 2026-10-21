package com.amandadamata.vetcare.repository;

import com.amandadamata.vetcare.model.Animal;
import com.amandadamata.vetcare.model.Owner;
import com.amandadamata.vetcare.model.Species;
import com.amandadamata.vetcare.persistence.ClinicSnapshot;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClinicRepositoryTest {

    @Test
    void loadReplacesEverythingInMemoryWithTheSnapshot() {
        ClinicRepository repository = new ClinicRepository();
        repository.saveOwner(new Owner(UUID.randomUUID(), "Old Owner", "+1 202-555-0100", ""));
        Owner owner = new Owner(UUID.randomUUID(), "Loaded Owner", "+1 202-555-0101", "");
        Animal animal = new Animal(UUID.randomUUID(), owner.getId(), "Luna", Species.DOG,
                LocalDate.of(2021, 5, 10), "");

        repository.load(new ClinicSnapshot(List.of(owner), List.of(animal), List.of()));

        assertAll(
                () -> assertEquals(List.of(owner), repository.allOwners()),
                () -> assertEquals(List.of(animal), repository.allAnimals()),
                () -> assertTrue(repository.allAppointments().isEmpty()),
                () -> assertEquals(animal, repository.findAnimal(animal.getId()).orElseThrow()));
    }

    @Test
    void snapshotIsACopyThatLaterChangesDoNotAffect() {
        ClinicRepository repository = new ClinicRepository();
        repository.saveOwner(new Owner(UUID.randomUUID(), "First Owner", "+1 202-555-0100", ""));

        ClinicSnapshot snapshot = repository.snapshot();
        repository.saveOwner(new Owner(UUID.randomUUID(), "Second Owner", "+1 202-555-0101", ""));

        assertAll(
                () -> assertEquals(1, snapshot.owners().size()),
                () -> assertEquals(2, repository.allOwners().size()));
    }
}
