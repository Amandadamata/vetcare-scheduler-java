package com.amandadamata.vetcare.service;

import com.amandadamata.vetcare.exception.NotFoundException;
import com.amandadamata.vetcare.model.Owner;
import com.amandadamata.vetcare.persistence.PersistenceCoordinator;
import com.amandadamata.vetcare.repository.ClinicRepository;
import com.amandadamata.vetcare.util.Validators;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class OwnerService {
    private final ClinicRepository repository;
    private final PersistenceCoordinator persistence;

    public OwnerService(ClinicRepository repository, PersistenceCoordinator persistence) {
        this.repository = repository;
        this.persistence = persistence;
    }

    public Owner register(String name, String phone, String email) {
        Owner owner = new Owner(
                UUID.randomUUID(),
                Validators.requiredText(name, "Owner name", 100),
                Validators.phone(phone),
                Validators.email(email));
        repository.saveOwner(owner);
        persistence.save();
        return owner;
    }

    public Owner update(UUID id, String name, String phone, String email) {
        Owner owner = findById(id);
        owner.update(
                Validators.requiredText(name, "Owner name", 100),
                Validators.phone(phone),
                Validators.email(email));
        repository.saveOwner(owner);
        persistence.save();
        return owner;
    }

    public Owner findById(UUID id) {
        return repository.findOwner(id)
                .orElseThrow(() -> new NotFoundException("Owner not found: " + id));
    }

    public List<Owner> listAll() {
        return repository.allOwners().stream()
                .sorted(Comparator.comparing(Owner::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }
}

