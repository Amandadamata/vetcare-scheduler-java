package com.amandadamata.vetcare.persistence;

public interface ClinicDataStore {
    ClinicSnapshot load();

    void save(ClinicSnapshot snapshot);
}

