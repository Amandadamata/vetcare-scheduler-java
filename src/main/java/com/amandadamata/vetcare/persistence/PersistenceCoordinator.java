package com.amandadamata.vetcare.persistence;

import com.amandadamata.vetcare.exception.DataPersistenceException;
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

    /**
     * Saves, and if saving fails puts the repository back to {@code before}.
     *
     * <p>Used by operations that remove or replace whole records (deletion and anonymization), so
     * a failed save never leaves personal data deleted in memory but still on disk, or the other
     * way around. {@code before} must be taken before the change with
     * {@link ClinicRepository#snapshot()}.
     */
    public void saveOrRestore(ClinicSnapshot before) {
        try {
            save();
        } catch (DataPersistenceException exception) {
            repository.load(before);
            throw exception;
        }
    }
}
