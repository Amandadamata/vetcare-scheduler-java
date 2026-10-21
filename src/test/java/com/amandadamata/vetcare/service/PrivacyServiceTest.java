package com.amandadamata.vetcare.service;

import com.amandadamata.vetcare.exception.DataPersistenceException;
import com.amandadamata.vetcare.exception.NotFoundException;
import com.amandadamata.vetcare.exception.ValidationException;
import com.amandadamata.vetcare.model.Animal;
import com.amandadamata.vetcare.model.Appointment;
import com.amandadamata.vetcare.model.AppointmentStatus;
import com.amandadamata.vetcare.model.AppointmentType;
import com.amandadamata.vetcare.model.Owner;
import com.amandadamata.vetcare.model.PatientCondition;
import com.amandadamata.vetcare.model.ScheduleResource;
import com.amandadamata.vetcare.model.Species;
import com.amandadamata.vetcare.model.TriagePriority;
import com.amandadamata.vetcare.persistence.ClinicDataStore;
import com.amandadamata.vetcare.persistence.ClinicSnapshot;
import com.amandadamata.vetcare.persistence.JsonClinicDataStore;
import com.amandadamata.vetcare.persistence.PersistenceCoordinator;
import com.amandadamata.vetcare.repository.ClinicRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrivacyServiceTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-24T13:00:00Z"), ZoneId.of("America/Sao_Paulo"));
    private static final UUID RESOURCE_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");

    private ClinicRepository repository;
    private SwitchableDataStore dataStore;
    private PrivacyService privacy;
    private Owner owner;
    private Animal luna;
    private Animal max;
    private Owner otherOwner;
    private Animal otherAnimal;

    @BeforeEach
    void setUp() {
        repository = new ClinicRepository();
        dataStore = new SwitchableDataStore();
        PersistenceCoordinator persistence = new PersistenceCoordinator(repository, dataStore);
        privacy = new PrivacyService(repository, persistence);

        repository.saveResource(new ScheduleResource(RESOURCE_ID, "Dr. Ana"));
        owner = new Owner(UUID.randomUUID(), "Test Owner", "+1 202-555-0100", "owner@example.com");
        luna = new Animal(UUID.randomUUID(), owner.getId(), "Luna", Species.DOG, LocalDate.of(2021, 5, 10), "Shy");
        max = new Animal(UUID.randomUUID(), owner.getId(), "Max", Species.CAT, LocalDate.of(2020, 1, 1), "");
        otherOwner = new Owner(UUID.randomUUID(), "Other Owner", "+1 202-555-0101", "");
        otherAnimal = new Animal(UUID.randomUUID(), otherOwner.getId(), "Rex", Species.DOG, LocalDate.of(2019, 1, 1), "");
        repository.saveOwner(owner);
        repository.saveOwner(otherOwner);
        repository.saveAnimal(luna);
        repository.saveAnimal(max);
        repository.saveAnimal(otherAnimal);
        repository.saveAppointment(appointment(luna, AppointmentStatus.COMPLETED, "Owner phone in notes"));
        repository.saveAppointment(appointment(max, AppointmentStatus.CANCELLED, ""));
        repository.saveAppointment(appointment(otherAnimal, AppointmentStatus.SCHEDULED, ""));
    }

    @Test
    void impactCountsAnimalsAndAppointments() {
        assertAll(
                () -> assertEquals(new PrivacyService.Impact(2, 2), privacy.impactOfOwner(owner.getId())),
                () -> assertEquals(new PrivacyService.Impact(1, 1), privacy.impactOfAnimal(luna.getId())));
    }

    @Test
    void deletingAnOwnerRemovesTheirAnimalsAndAppointmentsAndNothingElse() {
        privacy.deleteOwnerAndRecords(owner.getId());

        assertAll(
                () -> assertTrue(repository.findOwner(owner.getId()).isEmpty()),
                () -> assertTrue(repository.findAnimal(luna.getId()).isEmpty()),
                () -> assertTrue(repository.findAnimal(max.getId()).isEmpty()),
                () -> assertEquals(1, repository.allAppointments().size()),
                () -> assertEquals(otherAnimal.getId(), repository.allAppointments().get(0).getAnimalId()),
                () -> assertTrue(repository.findOwner(otherOwner.getId()).isPresent()),
                () -> assertEquals(1, dataStore.saveCount));
        assertNoBrokenReferences();
    }

    @Test
    void ownersWithScheduledAppointmentsCannotBeDeletedOrAnonymized() {
        ValidationException error = assertThrows(ValidationException.class,
                () -> privacy.deleteOwnerAndRecords(otherOwner.getId()));

        assertAll(
                () -> assertTrue(error.getMessage().contains("1 scheduled appointment"), error.getMessage()),
                () -> assertThrows(ValidationException.class, () -> privacy.anonymizeOwner(otherOwner.getId())),
                () -> assertThrows(ValidationException.class, () -> privacy.deleteAnimalAndRecords(otherAnimal.getId())),
                () -> assertTrue(repository.findOwner(otherOwner.getId()).isPresent()),
                () -> assertEquals(0, dataStore.saveCount));
    }

    @Test
    void anonymizingKeepsHistoryButRemovesPersonalDataAndNotes() {
        privacy.anonymizeOwner(owner.getId());

        Owner anonymized = repository.findOwner(owner.getId()).orElseThrow();
        Animal anonymizedLuna = repository.findAnimal(luna.getId()).orElseThrow();
        Appointment lunaAppointment = repository.allAppointments().stream()
                .filter(appointment -> appointment.getAnimalId().equals(luna.getId())).findFirst().orElseThrow();
        assertAll(
                () -> assertTrue(anonymized.isAnonymized()),
                () -> assertEquals(Owner.ANONYMIZED_NAME, anonymized.getName()),
                () -> assertEquals("", anonymized.getPhone()),
                () -> assertEquals("", anonymized.getEmail()),
                () -> assertEquals(Animal.ANONYMIZED_NAME, anonymizedLuna.getName()),
                () -> assertEquals("", anonymizedLuna.getNotes()),
                () -> assertEquals(Species.DOG, anonymizedLuna.getSpecies()),
                () -> assertEquals("", lunaAppointment.getNotes()),
                () -> assertEquals(AppointmentStatus.COMPLETED, lunaAppointment.getStatus()),
                () -> assertEquals(3, repository.allAppointments().size()),
                () -> assertThrows(ValidationException.class, () -> privacy.anonymizeOwner(owner.getId())));
        assertNoBrokenReferences();
    }

    @Test
    void anonymizedOwnersCannotBeEditedOrReceiveAnimals() {
        privacy.anonymizeOwner(owner.getId());
        PersistenceCoordinator persistence = new PersistenceCoordinator(repository, dataStore);
        OwnerService owners = new OwnerService(repository, persistence);
        AnimalService animals = new AnimalService(repository, persistence, CLOCK);

        assertAll(
                () -> assertThrows(ValidationException.class,
                        () -> owners.update(owner.getId(), "Real Name", "+1 202-555-0100", "")),
                () -> assertThrows(ValidationException.class,
                        () -> animals.register(owner.getId(), "New", Species.DOG, LocalDate.of(2020, 1, 1), "")),
                () -> assertThrows(ValidationException.class,
                        () -> animals.update(luna.getId(), "Luna", Species.DOG, LocalDate.of(2021, 5, 10), "")));
    }

    @Test
    void deletingAnAnimalRemovesOnlyItsAppointments() {
        privacy.deleteAnimalAndRecords(luna.getId());

        assertAll(
                () -> assertTrue(repository.findAnimal(luna.getId()).isEmpty()),
                () -> assertTrue(repository.findAnimal(max.getId()).isPresent()),
                () -> assertEquals(2, repository.allAppointments().size()),
                () -> assertTrue(repository.findOwner(owner.getId()).isPresent()));
        assertNoBrokenReferences();
    }

    @Test
    void failedSaveRestoresEverythingThatWasDeletedOrAnonymized() {
        dataStore.failSaves = true;

        assertThrows(DataPersistenceException.class, () -> privacy.deleteOwnerAndRecords(owner.getId()));
        assertThrows(DataPersistenceException.class, () -> privacy.anonymizeOwner(owner.getId()));
        assertThrows(DataPersistenceException.class, () -> privacy.deleteAnimalAndRecords(luna.getId()));

        assertAll(
                () -> assertEquals("Test Owner", repository.findOwner(owner.getId()).orElseThrow().getName()),
                () -> assertFalse(repository.findOwner(owner.getId()).orElseThrow().isAnonymized()),
                () -> assertEquals("Luna", repository.findAnimal(luna.getId()).orElseThrow().getName()),
                () -> assertEquals(3, repository.allAnimals().size()),
                () -> assertEquals(3, repository.allAppointments().size()));
    }

    @Test
    void unknownRecordsAreReported() {
        assertAll(
                () -> assertThrows(NotFoundException.class, () -> privacy.deleteOwnerAndRecords(UUID.randomUUID())),
                () -> assertThrows(NotFoundException.class, () -> privacy.anonymizeOwner(UUID.randomUUID())),
                () -> assertThrows(NotFoundException.class, () -> privacy.deleteAnimalAndRecords(UUID.randomUUID())));
    }

    @Test
    void anonymizedDataIsSavedAndLoadedAgainFromTheJsonFile(@TempDir Path folder) {
        JsonClinicDataStore store = new JsonClinicDataStore(folder.resolve("clinic.json"));
        ClinicRepository fileRepository = new ClinicRepository();
        fileRepository.load(repository.snapshot());
        new PrivacyService(fileRepository, new PersistenceCoordinator(fileRepository, store))
                .anonymizeOwner(owner.getId());

        ClinicSnapshot reloaded = store.load();

        Owner reloadedOwner = reloaded.owners().stream()
                .filter(candidate -> candidate.getId().equals(owner.getId())).findFirst().orElseThrow();
        assertAll(
                () -> assertTrue(reloadedOwner.isAnonymized()),
                () -> assertEquals("", reloadedOwner.getPhone()),
                () -> assertEquals(3, reloaded.appointments().size()));
    }

    private void assertNoBrokenReferences() {
        for (Animal animal : repository.allAnimals()) {
            assertTrue(repository.findOwner(animal.getOwnerId()).isPresent(), "animal without owner");
        }
        for (Appointment appointment : repository.allAppointments()) {
            assertTrue(repository.findAnimal(appointment.getAnimalId()).isPresent(), "appointment without animal");
        }
    }

    private static Appointment appointment(Animal animal, AppointmentStatus status, String notes) {
        return new Appointment(UUID.randomUUID(), animal.getId(), RESOURCE_ID, AppointmentType.CONSULTATION,
                LocalDateTime.of(2026, 9, 25, 9, 0), 30, PatientCondition.STABLE, Set.of(), notes,
                TriagePriority.STANDARD, status, Instant.parse("2026-09-20T13:00:00Z"));
    }

    private static final class SwitchableDataStore implements ClinicDataStore {
        private int saveCount;
        private boolean failSaves;

        @Override
        public ClinicSnapshot load() {
            return ClinicSnapshot.empty();
        }

        @Override
        public void save(ClinicSnapshot snapshot) {
            if (failSaves) {
                throw new DataPersistenceException("Simulated disk failure.", null);
            }
            saveCount++;
        }
    }
}
