package com.amandadamata.vetcare.persistence;

import com.amandadamata.vetcare.exception.DataPersistenceException;
import com.google.gson.Gson;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class JsonClinicDataStore implements ClinicDataStore {
    private final Path dataFile;
    private final Gson gson;

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
            ClinicSnapshot snapshot = gson.fromJson(json, ClinicSnapshot.class);
            return snapshot == null ? ClinicSnapshot.empty() : snapshot;
        } catch (IOException | RuntimeException exception) {
            throw new DataPersistenceException(
                    "Could not read clinic data from " + dataFile + ". The file was not changed.", exception);
        }
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
