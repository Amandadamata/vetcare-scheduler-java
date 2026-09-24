package com.amandadamata.vetcare.persistence;

import com.amandadamata.vetcare.model.Animal;
import com.amandadamata.vetcare.model.Appointment;
import com.amandadamata.vetcare.model.Owner;

import java.util.List;

public record ClinicSnapshot(List<Owner> owners, List<Animal> animals, List<Appointment> appointments) {
    public ClinicSnapshot {
        owners = owners == null ? List.of() : List.copyOf(owners);
        animals = animals == null ? List.of() : List.copyOf(animals);
        appointments = appointments == null ? List.of() : List.copyOf(appointments);
    }

    public static ClinicSnapshot empty() {
        return new ClinicSnapshot(List.of(), List.of(), List.of());
    }
}

