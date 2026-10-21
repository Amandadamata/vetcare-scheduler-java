package com.amandadamata.vetcare;

import com.amandadamata.vetcare.cli.VetCareCli;
import com.amandadamata.vetcare.exception.DataPersistenceException;
import com.amandadamata.vetcare.persistence.JsonClinicDataStore;
import com.amandadamata.vetcare.persistence.PersistenceCoordinator;
import com.amandadamata.vetcare.repository.ClinicRepository;
import com.amandadamata.vetcare.service.AnimalService;
import com.amandadamata.vetcare.service.AppointmentService;
import com.amandadamata.vetcare.service.OwnerService;
import com.amandadamata.vetcare.service.PrivacyService;
import com.amandadamata.vetcare.service.ReportService;
import com.amandadamata.vetcare.service.ScheduleResourceService;
import com.amandadamata.vetcare.service.ScheduleConflictService;
import com.amandadamata.vetcare.service.TriageService;

import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Clock;

public final class Main {
    private static final String DATA_FILE_ENVIRONMENT_VARIABLE = "VETCARE_DATA_FILE";
    private static final Path DEFAULT_DATA_FILE = Path.of("data", "vetcare-data.json");

    private Main() {
    }

    public static void main(String[] args) {
        PrintWriter output = new PrintWriter(System.out, true, StandardCharsets.UTF_8);
        PrintWriter error = new PrintWriter(System.err, true, StandardCharsets.UTF_8);

        Path dataFile;
        try {
            dataFile = resolveDataFile(System.getenv(DATA_FILE_ENVIRONMENT_VARIABLE));
        } catch (InvalidPathException exception) {
            error.printf("VetCare Scheduler could not start: %s contains an invalid file path.%n",
                    DATA_FILE_ENVIRONMENT_VARIABLE);
            error.println("Path details: " + exception.getInput());
            return;
        }

        ClinicRepository repository = new ClinicRepository();
        JsonClinicDataStore dataStore = new JsonClinicDataStore(dataFile);
        PersistenceCoordinator persistence = new PersistenceCoordinator(repository, dataStore);

        try {
            persistence.load();
        } catch (DataPersistenceException exception) {
            error.println("VetCare Scheduler could not load its saved data and will not start.");
            error.println(exception.getMessage());
            error.println("Correct or replace the data file, or set VETCARE_DATA_FILE to a readable JSON file.");
            return;
        }

        dataStore.migrationBackup().ifPresent(backup -> {
            output.println("The data file was converted to the current format. Existing appointments were placed");
            output.println("in a schedule named \"Main schedule\". The original file was kept at:");
            output.println("  " + backup.toAbsolutePath().normalize());
        });

        Clock clock = Clock.systemDefaultZone();
        OwnerService ownerService = new OwnerService(repository, persistence);
        AnimalService animalService = new AnimalService(repository, persistence, clock);
        TriageService triageService = new TriageService();
        ScheduleConflictService conflictService = new ScheduleConflictService();
        AppointmentService appointmentService = new AppointmentService(
                repository,
                persistence,
                triageService,
                conflictService,
                clock);
        ReportService reportService = new ReportService(repository);
        ScheduleResourceService resourceService = new ScheduleResourceService(repository, persistence);
        PrivacyService privacyService = new PrivacyService(repository, persistence);

        output.println("Data file: " + dataFile.toAbsolutePath().normalize());
        new VetCareCli(
                ownerService,
                animalService,
                appointmentService,
                reportService,
                resourceService,
                privacyService,
                clock,
                new InputStreamReader(System.in, StandardCharsets.UTF_8),
                output)
                .run();
    }

    static Path resolveDataFile(String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) {
            return DEFAULT_DATA_FILE;
        }
        return Path.of(configuredPath.trim());
    }
}
