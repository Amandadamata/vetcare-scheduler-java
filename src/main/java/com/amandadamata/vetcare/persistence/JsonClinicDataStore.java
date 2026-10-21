package com.amandadamata.vetcare.persistence;

import com.amandadamata.vetcare.exception.DataPersistenceException;
import com.amandadamata.vetcare.exception.ValidationException;
import com.google.gson.Gson;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

public class JsonClinicDataStore implements ClinicDataStore {
    private final Path dataFile;
    private final Gson gson;
    private Path migrationBackup;

    public JsonClinicDataStore(Path dataFile) {
        this(dataFile, GsonFactory.create());
    }

    JsonClinicDataStore(Path dataFile, Gson gson) {
        this.dataFile = dataFile;
        this.gson = gson;
    }

    @Override
    public ClinicSnapshot load() {
        if (Files.notExists(dataFile)) {
            return ClinicSnapshot.empty();
        }
        try {
            String json = Files.readString(dataFile, StandardCharsets.UTF_8);
            if (json.isBlank()) {
                return ClinicSnapshot.empty();
            }
            ClinicSnapshot parsed = gson.fromJson(json, ClinicSnapshot.class);
            ClinicSnapshot snapshot = parsed == null ? ClinicSnapshot.empty() : parsed;
            if (snapshot.schemaVersion() > ClinicSnapshot.CURRENT_SCHEMA_VERSION) {
                throw new DataPersistenceException("Clinic data in " + dataFile + " was written by a newer"
                        + " version of VetCare (format " + snapshot.schemaVersion() + "). The file was not changed.",
                        null);
            }
            if (ClinicDataMigration.isOutdated(snapshot)) {
                return migrate(snapshot);
            }
            ClinicSnapshotValidator.validate(snapshot);
            return snapshot;
        } catch (DataPersistenceException exception) {
            throw exception;
        } catch (ValidationException exception) {
            throw new DataPersistenceException(
                    "Clinic data in " + dataFile + " is invalid: " + exception.getMessage()
                            + " The file was not changed.",
                    exception);
        } catch (IOException | RuntimeException exception) {
            throw new DataPersistenceException(
                    "Could not read clinic data from " + dataFile + ". The file was not changed.", exception);
        }
    }

    /**
     * Path of the backup created when an old file was migrated during the last {@link #load()},
     * so the application can tell the user where the original data is.
     */
    public Optional<Path> migrationBackup() {
        return Optional.ofNullable(migrationBackup);
    }

    /**
     * Converts an old file to the current format.
     *
     * <p>Order matters: the migrated data is validated first, while nothing has been written. Only
     * then is the original file copied to a backup (never overwriting an existing one) and the
     * migrated data saved with the usual atomic write. If anything fails, the original file is
     * still in place.
     */
    private ClinicSnapshot migrate(ClinicSnapshot old) throws IOException {
        ClinicSnapshot migrated = ClinicDataMigration.toCurrentVersion(old);
        ClinicSnapshotValidator.validate(migrated);
        Path backup = freeBackupPath();
        Files.copy(dataFile, backup);
        save(migrated);
        migrationBackup = backup;
        return migrated;
    }

    private Path freeBackupPath() {
        String baseName = dataFile.getFileName() + ".v1-backup";
        Path candidate = dataFile.resolveSibling(baseName + ".json");
        for (int counter = 2; Files.exists(candidate); counter++) {
            candidate = dataFile.resolveSibling(baseName + "-" + counter + ".json");
        }
        return candidate;
    }

    @Override
    public void save(ClinicSnapshot snapshot) {
        Path parent = dataFile.toAbsolutePath().getParent();
        Path temporaryFile = dataFile.resolveSibling(dataFile.getFileName() + ".tmp");
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(temporaryFile, gson.toJson(snapshot), StandardCharsets.UTF_8);
            moveIntoPlace(temporaryFile);
        } catch (IOException exception) {
            try {
                Files.deleteIfExists(temporaryFile);
            } catch (IOException ignored) {
                // Keep the original persistence error as the useful failure.
            }
            throw new DataPersistenceException("Could not save clinic data to " + dataFile + ".", exception);
        }
    }

    private void moveIntoPlace(Path temporaryFile) throws IOException {
        try {
            Files.move(
                    temporaryFile,
                    dataFile,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporaryFile, dataFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
