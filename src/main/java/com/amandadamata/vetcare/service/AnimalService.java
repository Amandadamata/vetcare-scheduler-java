package com.amandadamata.vetcare.service;

import com.amandadamata.vetcare.exception.NotFoundException;
import com.amandadamata.vetcare.model.Animal;
import com.amandadamata.vetcare.model.Species;
import com.amandadamata.vetcare.persistence.PersistenceCoordinator;
import com.amandadamata.vetcare.repository.ClinicRepository;
import com.amandadamata.vetcare.util.Validators;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class AnimalService {
    private final ClinicRepository repository;
    private final PersistenceCoordinator persistence;
    private final Clock clock;

    public AnimalService(ClinicRepository repository, PersistenceCoordinator persistence, Clock clock) {
        this.repository = repository;
        this.persistence = persistence;
        this.clock = clock;
    }

    public Animal register(
            UUID ownerId,
            String name,
            Species species,
            LocalDate birthDate,
            String notes) {
        requireOwner(ownerId);
        Animal animal = new Animal(
                UUID.randomUUID(),
                ownerId,
                Validators.requiredText(name, "Animal name", 100),
                requireSpecies(species),
                Validators.birthDate(birthDate, clock),
                Validators.optionalText(notes, "Notes", 500));
        repository.saveAnimal(animal);
        persistence.save();
        return animal;
    }

    public Animal update(UUID id, String name, Species species, LocalDate birthDate, String notes) {
        Animal animal = findById(id);
        animal.update(
                Validators.requiredText(name, "Animal name", 100),
                requireSpecies(species),
                Validators.birthDate(birthDate, clock),
                Validators.optionalText(notes, "Notes", 500));
        repository.saveAnimal(animal);
        persistence.save();
        return animal;
    }

    public Animal findById(UUID id) {
        return repository.findAnimal(id)
                .orElseThrow(() -> new NotFoundException("Animal not found: " + id));
    }

    public List<Animal> listAll() {
        return repository.allAnimals().stream()
                .sorted(Comparator.comparing(Animal::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public List<Animal> searchByName(String query) {
        String normalized = Validators.requiredText(query, "Search text", 100).toLowerCase(Locale.ROOT);
        return listAll().stream()
                .filter(animal -> animal.getName().toLowerCase(Locale.ROOT).contains(normalized))
                .toList();
    }

    private void requireOwner(UUID ownerId) {
        if (ownerId == null || repository.findOwner(ownerId).isEmpty()) {
            throw new NotFoundException("An existing owner is required for the animal.");
        }
    }

    private Species requireSpecies(Species species) {
        if (species == null) {
            throw new com.amandadamata.vetcare.exception.ValidationException("Species is required.");
        }
        return species;
    }
}

