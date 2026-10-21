package com.amandadamata.vetcare.service;

import com.amandadamata.vetcare.exception.DataPersistenceException;
import com.amandadamata.vetcare.exception.NotFoundException;
import com.amandadamata.vetcare.exception.ValidationException;
import com.amandadamata.vetcare.model.Animal;
import com.amandadamata.vetcare.model.Owner;
import com.amandadamata.vetcare.model.Species;
import com.amandadamata.vetcare.persistence.ClinicDataStore;
import com.amandadamata.vetcare.persistence.ClinicSnapshot;
import com.amandadamata.vetcare.persistence.PersistenceCoordinator;
import com.amandadamata.vetcare.repository.ClinicRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnerAndAnimalServiceTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-24T13:00:00Z"), ZoneId.of("America/Sao_Paulo"));

    private ClinicRepository repository;
    private SwitchableDataStore dataStore;
    private OwnerService ownerService;
    private AnimalService animalService;

    @BeforeEach
    void setUp() {
        repository = new ClinicRepository();
        dataStore = new SwitchableDataStore();
        PersistenceCoordinator persistence = new PersistenceCoordinator(repository, dataStore);
        ownerService = new OwnerService(repository, persistence);
        animalService = new AnimalService(repository, persistence, CLOCK);
    }

    @Test
    void registerOwnerNormalizesInputAndSavesTheSnapshot() {
        Owner owner = ownerService.register("  Test Owner  ", " +1 202-555-0100 ", "  ");

        assertAll(
                () -> assertEquals("Test Owner", owner.getName()),
                () -> assertEquals("+1 202-555-0100", owner.getPhone()),
                () -> assertEquals("", owner.getEmail()),
                () -> assertEquals(1, dataStore.saveCount),
                () -> assertEquals(List.of(owner), dataStore.saved.owners()));
    }

    @Test
    void invalidOwnerIsNotStoredOrSaved() {
        assertThrows(ValidationException.class,
                () -> ownerService.register("Test Owner", "123", "owner@example.com"));

        assertAll(
                () -> assertTrue(repository.allOwners().isEmpty()),
                () -> assertEquals(0, dataStore.saveCount));
    }

    @Test
    void updateOwnerReplacesFieldsAndRejectsUnknownId() {
        Owner owner = ownerService.register("Test Owner", "+1 202-555-0100", "");

        ownerService.update(owner.getId(), "Renamed Owner", "+1 202-555-0199", "new@example.com");

        assertAll(
                () -> assertEquals("Renamed Owner", ownerService.findById(owner.getId()).getName()),
                () -> assertEquals("new@example.com", ownerService.findById(owner.getId()).getEmail()),
                () -> assertThrows(NotFoundException.class,
                        () -> ownerService.update(UUID.randomUUID(), "Name", "+1 202-555-0100", "")));
    }

    @Test
    void ownersAreListedAlphabeticallyIgnoringCase() {
        ownerService.register("carla", "+1 202-555-0101", "");
        ownerService.register("Ana", "+1 202-555-0102", "");
        ownerService.register("bruno", "+1 202-555-0103", "");

        List<String> names = ownerService.listAll().stream().map(Owner::getName).toList();

        assertEquals(List.of("Ana", "bruno", "carla"), names);
    }

    @Test
    void animalRequiresAnExistingOwner() {
        assertThrows(NotFoundException.class, () -> animalService.register(
                UUID.randomUUID(), "Luna", Species.DOG, LocalDate.of(2021, 5, 10), ""));
        assertThrows(NotFoundException.class, () -> animalService.register(
                null, "Luna", Species.DOG, LocalDate.of(2021, 5, 10), ""));

        assertTrue(repository.allAnimals().isEmpty());
    }

    @Test
    void animalRejectsFutureBirthDateAndMissingSpecies() {
        Owner owner = ownerService.register("Test Owner", "+1 202-555-0100", "");

        assertAll(
                () -> assertThrows(ValidationException.class, () -> animalService.register(
                        owner.getId(), "Luna", Species.DOG, LocalDate.of(2026, 9, 25), "")),
                () -> assertThrows(ValidationException.class, () -> animalService.register(
                        owner.getId(), "Luna", null, LocalDate.of(2021, 5, 10), "")),
                () -> assertTrue(repository.allAnimals().isEmpty()));
    }

    @Test
    void searchByNameIsCaseInsensitiveAndPartial() {
        Owner owner = ownerService.register("Test Owner", "+1 202-555-0100", "");
        Animal luna = animalService.register(owner.getId(), "Luna", Species.DOG, LocalDate.of(2021, 5, 10), "");
        animalService.register(owner.getId(), "Max", Species.CAT, LocalDate.of(2020, 1, 1), "");

        assertAll(
                () -> assertEquals(List.of(luna), animalService.searchByName("LUN")),
                () -> assertTrue(animalService.searchByName("zzz").isEmpty()),
                () -> assertThrows(ValidationException.class, () -> animalService.searchByName("  ")));
    }

    @Test
    void failedSaveIsReportedAndTheNextSuccessfulSaveIncludesTheChange() {
        dataStore.failNextSave = true;

        assertThrows(DataPersistenceException.class,
                () -> ownerService.register("First Owner", "+1 202-555-0100", ""));
        // Known limitation, shown to the user by the CLI: the change stays in memory.
        assertEquals(1, repository.allOwners().size());

        ownerService.register("Second Owner", "+1 202-555-0101", "");

        assertEquals(2, dataStore.saved.owners().size());
    }

    private static final class SwitchableDataStore implements ClinicDataStore {
        private ClinicSnapshot saved = ClinicSnapshot.empty();
        private int saveCount;
        private boolean failNextSave;

        @Override
        public ClinicSnapshot load() {
            return saved;
        }

        @Override
        public void save(ClinicSnapshot snapshot) {
            if (failNextSave) {
                failNextSave = false;
                throw new DataPersistenceException("Simulated disk failure.", null);
            }
            saved = snapshot;
            saveCount++;
        }
    }
}
