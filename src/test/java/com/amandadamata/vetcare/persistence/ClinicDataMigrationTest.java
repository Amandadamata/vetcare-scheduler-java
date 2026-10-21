package com.amandadamata.vetcare.persistence;

import com.amandadamata.vetcare.exception.DataPersistenceException;
import com.amandadamata.vetcare.model.Appointment;
import com.amandadamata.vetcare.model.AppointmentStatus;
import com.amandadamata.vetcare.model.ScheduleResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The fixture was written by the previous release (commit 02c2e8d), with fictional data, so these
 * tests check compatibility with a real old file rather than with a hand-made imitation.
 */
class ClinicDataMigrationTest {
    private static final String FIXTURE = "/migration/v1-written-by-release-02c2e8d.json";

    @TempDir
    Path folder;

    private Path dataFile;
    private byte[] originalBytes;

    @BeforeEach
    void copyOldFile() throws IOException {
        dataFile = folder.resolve("vetcare-data.json");
        try (InputStream fixture = Objects.requireNonNull(getClass().getResourceAsStream(FIXTURE))) {
            originalBytes = fixture.readAllBytes();
        }
        Files.write(dataFile, originalBytes);
    }

    @Test
    void oldFileIsMigratedAfterABackupAndKeepsEveryRecord() throws IOException {
        JsonClinicDataStore store = new JsonClinicDataStore(dataFile);

        ClinicSnapshot migrated = store.load();

        Path backup = folder.resolve("vetcare-data.json.v1-backup.json");
        ScheduleResource mainSchedule = migrated.resources().get(0);
        assertAll(
                () -> assertEquals(ClinicSnapshot.CURRENT_SCHEMA_VERSION, migrated.schemaVersion()),
                () -> assertEquals(1, migrated.resources().size()),
                () -> assertEquals(ClinicDataMigration.MAIN_SCHEDULE_NAME, mainSchedule.getName()),
                () -> assertTrue(migrated.appointments().stream()
                        .allMatch(appointment -> mainSchedule.getId().equals(appointment.getResourceId()))),
                () -> assertEquals("Test Owner", migrated.owners().get(0).getName()),
                () -> assertEquals("owner@example.com", migrated.owners().get(0).getEmail()),
                () -> assertEquals("Allergic to chicken", migrated.animals().get(0).getNotes()),
                () -> assertEquals(List.of(AppointmentStatus.SCHEDULED, AppointmentStatus.CANCELLED),
                        migrated.appointments().stream().map(Appointment::getStatus).toList()),
                () -> assertEquals("Bring previous exams", migrated.appointments().get(0).getNotes()),
                () -> assertEquals(backup, store.migrationBackup().orElseThrow()),
                () -> assertArrayEquals(originalBytes, Files.readAllBytes(backup), "backup must be identical"),
                () -> assertTrue(Files.readString(dataFile).contains("\"schemaVersion\": 2")));
    }

    @Test
    void migratedFileLoadsNormallyAndIsNotMigratedTwice() throws IOException {
        new JsonClinicDataStore(dataFile).load();
        JsonClinicDataStore secondStart = new JsonClinicDataStore(dataFile);

        ClinicSnapshot loadedAgain = secondStart.load();

        try (var files = Files.list(folder)) {
            assertAll(
                    () -> assertTrue(secondStart.migrationBackup().isEmpty()),
                    () -> assertEquals(2, loadedAgain.appointments().size()),
                    () -> assertEquals(2, files.count(), "only the data file and one backup"));
        }
    }

    @Test
    void anExistingBackupIsNeverOverwritten() throws IOException {
        Path earlierBackup = folder.resolve("vetcare-data.json.v1-backup.json");
        Files.writeString(earlierBackup, "earlier backup");

        JsonClinicDataStore store = new JsonClinicDataStore(dataFile);
        store.load();

        assertAll(
                () -> assertEquals("earlier backup", Files.readString(earlierBackup)),
                () -> assertEquals(folder.resolve("vetcare-data.json.v1-backup-2.json"),
                        store.migrationBackup().orElseThrow()));
    }

    @Test
    void invalidOldDataIsNotMigratedAndNothingIsWritten() throws IOException {
        String invalid = new String(originalBytes, StandardCharsets.UTF_8)
                .replaceFirst("\"durationMinutes\": 30", "\"durationMinutes\": 5000");
        Files.writeString(dataFile, invalid);

        DataPersistenceException error = assertThrows(DataPersistenceException.class,
                () -> new JsonClinicDataStore(dataFile).load());

        try (var files = Files.list(folder)) {
            assertAll(
                    () -> assertTrue(error.getMessage().contains("The file was not changed."), error.getMessage()),
                    () -> assertEquals(invalid, Files.readString(dataFile)),
                    () -> assertEquals(1, files.count(), "no backup is created for data that cannot be migrated"));
        }
    }

    @Test
    void fileFromANewerVersionIsRefusedUntouched() throws IOException {
        String newer = "{\"schemaVersion\": 3, \"owners\": []}";
        Files.writeString(dataFile, newer);

        DataPersistenceException error = assertThrows(DataPersistenceException.class,
                () -> new JsonClinicDataStore(dataFile).load());

        assertAll(
                () -> assertTrue(error.getMessage().contains("newer version"), error.getMessage()),
                () -> assertEquals(newer, Files.readString(dataFile)));
    }

    @Test
    void migrationAssignsOnlyAppointmentsWithoutSchedule() {
        UUID existingSchedule = UUID.randomUUID();
        ClinicSnapshot alreadyAssigned = new ClinicSnapshot(1,
                List.of(new ScheduleResource(existingSchedule, "Dr. Ana")), List.of(), List.of(), List.of());

        ClinicSnapshot migrated = ClinicDataMigration.toCurrentVersion(alreadyAssigned);

        assertAll(
                () -> assertEquals(1, migrated.resources().size(), "no main schedule when nothing needs it"),
                () -> assertFalse(ClinicDataMigration.isOutdated(migrated)));
    }
}
