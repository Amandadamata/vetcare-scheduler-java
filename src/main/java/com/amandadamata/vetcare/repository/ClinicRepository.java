package com.amandadamata.vetcare.repository;

import com.amandadamata.vetcare.model.Animal;
import com.amandadamata.vetcare.model.Appointment;
import com.amandadamata.vetcare.model.Owner;
import com.amandadamata.vetcare.persistence.ClinicSnapshot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class ClinicRepository {
    private final Map<UUID, Owner> owners = new LinkedHashMap<>();
    private final Map<UUID, Animal> animals = new LinkedHashMap<>();
    private final Map<UUID, Appointment> appointments = new LinkedHashMap<>();

    public void load(ClinicSnapshot snapshot) {
        owners.clear();
        animals.clear();
        appointments.clear();
        snapshot.owners().forEach(owner -> owners.put(owner.getId(), owner));
        snapshot.animals().forEach(animal -> animals.put(animal.getId(), animal));
        snapshot.appointments().forEach(appointment -> appointments.put(appointment.getId(), appointment));
    }

    public ClinicSnapshot snapshot() {
        return new ClinicSnapshot(allOwners(), allAnimals(), allAppointments());
    }

    public void saveOwner(Owner owner) {
        owners.put(owner.getId(), owner);
    }

    public void saveAnimal(Animal animal) {
        animals.put(animal.getId(), animal);
    }

    public void saveAppointment(Appointment appointment) {
        appointments.put(appointment.getId(), appointment);
    }

    public Optional<Owner> findOwner(UUID id) {
        return Optional.ofNullable(owners.get(id));
    }

    public Optional<Animal> findAnimal(UUID id) {
        return Optional.ofNullable(animals.get(id));
    }

    public Optional<Appointment> findAppointment(UUID id) {
        return Optional.ofNullable(appointments.get(id));
    }

    public List<Owner> allOwners() {
        return new ArrayList<>(owners.values());
    }

    public List<Animal> allAnimals() {
        return new ArrayList<>(animals.values());
    }

    public List<Appointment> allAppointments() {
        return new ArrayList<>(appointments.values());
    }
}

