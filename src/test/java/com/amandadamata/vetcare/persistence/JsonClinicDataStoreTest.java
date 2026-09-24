package com.amandadamata.vetcare.persistence;

import com.amandadamata.vetcare.exception.DataPersistenceException;
import com.amandadamata.vetcare.model.Animal;
import com.amandadamata.vetcare.model.Appointment;
import com.amandadamata.vetcare.model.AppointmentStatus;
import com.amandadamata.vetcare.model.AppointmentType;
import com.amandadamata.vetcare.model.ClinicalSign;
import com.amandadamata.vetcare.model.Owner;
import com.amandadamata.vetcare.model.PatientCondition;
import com.amandadamata.vetcare.model.Species;
import com.amandadamata.vetcare.model.TriagePriority;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonClinicDataStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void roundTripPreservesEveryDomainFieldAndCreatesParentDirectories() {
        UUID ownerId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        UUID animalId = UUID.fromString("20000000-0000-0000-0000-000000000001");
        UUID appointmentId = UUID.fromString("30000000-0000-0000-0000-000000000001");
        Owner owner = new Owner(ownerId, "Amanda", "+55 62 99914-4445", "amanda@example.com");
        Animal animal = new Animal(
                animalId,
                ownerId,
                "Luna",
                Species.DOG,
                LocalDate.of(2021, 5, 10),
                "Allergic to chicken");
        Appointment appointment = new Appointment(
                appointmentId,
                animalId,
                AppointmentType.EXAM,
                LocalDateTime.of(2026, 9, 25, 14, 30),
                45,
                PatientCondition.CONCERNING,
                Set.of(ClinicalSign.SEVERE_PAIN, ClinicalSign.REPEATED_VOMITING),
                "Bring previous results",
                TriagePriority.URGENT,
                AppointmentStatus.SCHEDULED,
                Instant.parse("2026-09-24T13:00:00Z"));
        Path dataFile = tempDir.resolve("nested/data/clinic.json");
        JsonClinicDataStore store = new JsonClinicDataStore(dataFile);

        store.save(new ClinicSnapshot(List.of(owner), List.of(animal), List.of(appointment)));
        ClinicSnapshot loaded = store.load();

        assertTrue(Files.exists(dataFile));
        assertFalse(Files.exists(dataFile.resolveSibling("clinic.json.tmp")));
        assertEquals(1, loaded.owners().size());
        assertEquals(1, loaded.animals().size());
        assertEquals(1, loaded.appointments().size());
        Owner loadedOwner = loaded.owners().get(0);
        Animal loadedAnimal = loaded.animals().get(0);
        Appointment loadedAppointment = loaded.appointments().get(0);
        assertAll(
                () -> assertEquals(ownerId, loadedOwner.getId()),
                () -> assertEquals("Amanda", loadedOwner.getName()),
                () -> assertEquals("+55 62 99914-4445", loadedOwner.getPhone()),
                () -> assertEquals("amanda@example.com", loadedOwner.getEmail()),
                () -> assertEquals(animalId, loadedAnimal.getId()),
                () -> assertEquals(ownerId, loadedAnimal.getOwnerId()),
                () -> assertEquals("Luna", loadedAnimal.getName()),
                () -> assertEquals(Species.DOG, loadedAnimal.getSpecies()),
                () -> assertEquals(LocalDate.of(2021, 5, 10), loadedAnimal.getBirthDate()),
                () -> assertEquals("Allergic to chicken", loadedAnimal.getNotes()),
                () -> assertEquals(appointmentId, loadedAppointment.getId()),
                () -> assertEquals(animalId, loadedAppointment.getAnimalId()),
                () -> assertEquals(AppointmentType.EXAM, loadedAppointment.getType()),
                () -> assertEquals(LocalDateTime.of(2026, 9, 25, 14, 30), loadedAppointment.getStartTime()),
                () -> assertEquals(45, loadedAppointment.getDurationMinutes()),
                () -> assertEquals(PatientCondition.CONCERNING, loadedAppointment.getCondition()),
                () -> assertEquals(Set.of(ClinicalSign.SEVERE_PAIN, ClinicalSign.REPEATED_VOMITING),
                        loadedAppointment.getClinicalSigns()),
                () -> assertEquals("Bring previous results", loadedAppointment.getNotes()),
                () -> assertEquals(TriagePriority.URGENT, loadedAppointment.getPriority()),
                () -> assertEquals(AppointmentStatus.SCHEDULED, loadedAppointment.getStatus()),
                () -> assertEquals(Instant.parse("2026-09-24T13:00:00Z"), loadedAppointment.getCreatedAt()));
    }

    @Test
    void missingFileLoadsEmptySnapshotWithoutCreatingAFile() {
        Path dataFile = tempDir.resolve("missing.json");

        ClinicSnapshot loaded = new JsonClinicDataStore(dataFile).load();

        assertAll(
                () -> assertTrue(loaded.owners().isEmpty()),
                () -> assertTrue(loaded.animals().isEmpty()),
                () -> assertTrue(loaded.appointments().isEmpty()),
                () -> assertFalse(Files.exists(dataFile)));
    }

    @Test
    void blankFileLoadsEmptySnapshot() throws IOException {
        Path dataFile = tempDir.resolve("blank.json");
        Files.writeString(dataFile, " \n\t ", StandardCharsets.UTF_8);

        ClinicSnapshot loaded = new JsonClinicDataStore(dataFile).load();

        assertTrue(loaded.owners().isEmpty());
        assertTrue(loaded.animals().isEmpty());
        assertTrue(loaded.appointments().isEmpty());
    }

    @Test
    void invalidJsonThrowsPersistenceExceptionAndLeavesSourceUntouched() throws IOException {
        Path dataFile = tempDir.resolve("invalid.json");
        String invalidJson = "{ definitely-not-valid-json";
        Files.writeString(dataFile, invalidJson, StandardCharsets.UTF_8);

        DataPersistenceException exception = assertThrows(
                DataPersistenceException.class,
                () -> new JsonClinicDataStore(dataFile).load());

        assertAll(
                () -> assertTrue(exception.getMessage().contains(dataFile.toString())),
                () -> assertNotNull(exception.getCause()),
                () -> assertEquals(invalidJson, Files.readString(dataFile, StandardCharsets.UTF_8)));
    }
}
