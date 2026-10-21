package com.amandadamata.vetcare.service;

import com.amandadamata.vetcare.exception.NotFoundException;
import com.amandadamata.vetcare.exception.ValidationException;
import com.amandadamata.vetcare.model.Appointment;
import com.amandadamata.vetcare.model.AppointmentStatus;
import com.amandadamata.vetcare.model.AppointmentType;
import com.amandadamata.vetcare.model.PatientCondition;
import com.amandadamata.vetcare.model.ScheduleResource;
import com.amandadamata.vetcare.model.TriagePriority;
import com.amandadamata.vetcare.persistence.ClinicDataStore;
import com.amandadamata.vetcare.persistence.ClinicSnapshot;
import com.amandadamata.vetcare.persistence.PersistenceCoordinator;
import com.amandadamata.vetcare.repository.ClinicRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduleResourceServiceTest {
    private ClinicRepository repository;
    private ScheduleResourceService service;

    @BeforeEach
    void setUp() {
        repository = new ClinicRepository();
        ClinicDataStore store = new ClinicDataStore() {
            @Override
            public ClinicSnapshot load() {
                return ClinicSnapshot.empty();
            }

            @Override
            public void save(ClinicSnapshot snapshot) {
                // In-memory only.
            }
        };
        service = new ScheduleResourceService(repository, new PersistenceCoordinator(repository, store));
    }

    @Test
    void registersAndListsSchedulesAlphabetically() {
        service.register("  Room 2 ");
        service.register("Dr. Ana");

        assertEquals(List.of("Dr. Ana", "Room 2"),
                service.listAll().stream().map(ScheduleResource::getName).toList());
    }

    @Test
    void namesMustBePresentAndUniqueIgnoringCase() {
        service.register("Dr. Ana");

        assertAll(
                () -> assertThrows(ValidationException.class, () -> service.register("dr. ana")),
                () -> assertThrows(ValidationException.class, () -> service.register("  ")),
                () -> assertEquals(1, service.listAll().size()));
    }

    @Test
    void onlyUnusedSchedulesCanBeDeleted() {
        ScheduleResource used = service.register("Dr. Ana");
        ScheduleResource unused = service.register("Room 2");
        repository.saveAppointment(new Appointment(UUID.randomUUID(), UUID.randomUUID(), used.getId(),
                AppointmentType.EXAM, LocalDateTime.of(2026, 9, 25, 9, 0), 30, PatientCondition.STABLE, Set.of(),
                "", TriagePriority.STANDARD, AppointmentStatus.CANCELLED, Instant.parse("2026-09-20T13:00:00Z")));

        service.delete(unused.getId());

        assertAll(
                () -> assertThrows(ValidationException.class, () -> service.delete(used.getId())),
                () -> assertThrows(NotFoundException.class, () -> service.delete(UUID.randomUUID())),
                () -> assertTrue(repository.findResource(unused.getId()).isEmpty()),
                () -> assertTrue(repository.findResource(used.getId()).isPresent()));
    }
}
