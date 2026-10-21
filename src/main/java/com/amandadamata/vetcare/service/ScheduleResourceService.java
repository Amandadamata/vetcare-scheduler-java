package com.amandadamata.vetcare.service;

import com.amandadamata.vetcare.exception.NotFoundException;
import com.amandadamata.vetcare.exception.ValidationException;
import com.amandadamata.vetcare.model.ScheduleResource;
import com.amandadamata.vetcare.persistence.ClinicSnapshot;
import com.amandadamata.vetcare.persistence.PersistenceCoordinator;
import com.amandadamata.vetcare.repository.ClinicRepository;
import com.amandadamata.vetcare.util.Validators;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Schedule resources: veterinarians, rooms, or anything that attends one appointment at a time. */
public class ScheduleResourceService {
    private final ClinicRepository repository;
    private final PersistenceCoordinator persistence;

    public ScheduleResourceService(ClinicRepository repository, PersistenceCoordinator persistence) {
        this.repository = repository;
        this.persistence = persistence;
    }

    /** Names are unique, ignoring case, so two schedules cannot be confused on screen. */
    public ScheduleResource register(String name) {
        String validName = Validators.requiredText(name, "Schedule name", 100);
        boolean taken = repository.allResources().stream()
                .anyMatch(resource -> resource.getName().equalsIgnoreCase(validName));
        if (taken) {
            throw new ValidationException("A schedule named \"" + validName + "\" already exists.");
        }
        ScheduleResource resource = new ScheduleResource(UUID.randomUUID(), validName);
        repository.saveResource(resource);
        persistence.save();
        return resource;
    }

    public ScheduleResource findById(UUID id) {
        return repository.findResource(id)
                .orElseThrow(() -> new NotFoundException("Schedule not found: " + id));
    }

    public List<ScheduleResource> listAll() {
        return repository.allResources().stream()
                .sorted(Comparator.comparing(ScheduleResource::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** A schedule can only be removed while no appointment (of any status) refers to it. */
    public void delete(UUID id) {
        findById(id);
        boolean inUse = repository.allAppointments().stream()
                .anyMatch(appointment -> id.equals(appointment.getResourceId()));
        if (inUse) {
            throw new ValidationException("This schedule has appointments and cannot be removed.");
        }
        ClinicSnapshot before = repository.snapshot();
        repository.deleteResource(id);
        persistence.saveOrRestore(before);
    }
}
