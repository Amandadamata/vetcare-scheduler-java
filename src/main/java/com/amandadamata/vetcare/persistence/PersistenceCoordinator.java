package com.amandadamata.vetcare.persistence;

import com.amandadamata.vetcare.repository.ClinicRepository;

public class PersistenceCoordinator {
    private final ClinicRepository repository;
    private final ClinicDataStore dataStore;

    public PersistenceCoordinator(ClinicRepository repository, ClinicDataStore dataStore) {
        this.repository = repository;
        this.dataStore = dataStore;
    }

    public void load() {
        repository.load(dataStore.load());
    }

    public void save() {
        dataStore.save(repository.snapshot());
    }
}
