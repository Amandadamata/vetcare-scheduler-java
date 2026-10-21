package com.amandadamata.vetcare.persistence;

import com.amandadamata.vetcare.model.Animal;
import com.amandadamata.vetcare.model.Appointment;
import com.amandadamata.vetcare.model.Owner;
import com.amandadamata.vetcare.model.ScheduleResource;

import java.util.List;

/**
 * Everything the application stores, as one immutable value.
 *
 * @param schemaVersion format version of the JSON file. Files written before version 2 have no
 *                      such field, so Gson leaves it as {@code 0}.
 */
public record ClinicSnapshot(
        int schemaVersion,
        List<ScheduleResource> resources,
        List<Owner> owners,
        List<Animal> animals,
        List<Appointment> appointments) {

    /** Version written by this application. Version 2 added schedule resources. */
    public static final int CURRENT_SCHEMA_VERSION = 2;

    public ClinicSnapshot {
        resources = resources == null ? List.of() : List.copyOf(resources);
        owners = owners == null ? List.of() : List.copyOf(owners);
        animals = animals == null ? List.of() : List.copyOf(animals);
        appointments = appointments == null ? List.of() : List.copyOf(appointments);
    }

    public static ClinicSnapshot empty() {
        return new ClinicSnapshot(CURRENT_SCHEMA_VERSION, List.of(), List.of(), List.of(), List.of());
    }
}
