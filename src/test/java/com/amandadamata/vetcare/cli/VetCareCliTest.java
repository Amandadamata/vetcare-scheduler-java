package com.amandadamata.vetcare.cli;

import com.amandadamata.vetcare.exception.DataPersistenceException;
import com.amandadamata.vetcare.model.Appointment;
import com.amandadamata.vetcare.model.AppointmentStatus;
import com.amandadamata.vetcare.model.TriagePriority;
import com.amandadamata.vetcare.persistence.ClinicDataStore;
import com.amandadamata.vetcare.persistence.ClinicSnapshot;
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
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the real menu with simulated keyboard input. Each test reads like a user session; the
 * comments name the menu option being used.
 */
class VetCareCliTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-24T13:00:00Z"), ZoneId.of("America/Sao_Paulo"));

    private static final String[] REGISTER_OWNER_AND_ANIMAL = {
            "17", "Dr. Ana",                                // 17. Register schedule
            "1", "Test Owner", "+1 202-555-0100", "",       // 1. Register owner
            "2", "1", "Luna", "1", "2021-05-10", ""          // 2. Register animal (owner 1, Dog)
    };

    private final InMemoryDataStore dataStore = new InMemoryDataStore();

    @Test
    void completeAppointmentLifecycle() {
        String output = run(
                REGISTER_OWNER_AND_ANIMAL,
                "3", "1", "1", "2026-09-25 09:00", "30", "1", "5", "", // 3. Schedule: severe pain
                "15", "2026-09-25",                                  // 15. Triage queue
                "14", "1", "yes",                                    // 14. Cancel appointment 1
                "16", "2026-09-25",                                  // 16. Daily summary
                "0");

        assertAll(
                () -> assertTrue(output.contains("Owner registered successfully."), output),
                () -> assertTrue(output.contains("Animal registered successfully."), output),
                () -> assertTrue(output.contains("Appointment scheduled successfully with Urgent priority."), output),
                () -> assertTrue(output.contains("Triage queue for 2026-09-25 (1)"), output),
                () -> assertTrue(output.contains("Appointment cancelled successfully."), output),
                () -> assertTrue(output.contains("Cancelled: 1"), output),
                () -> assertTrue(output.contains("Goodbye. Clinic data was saved after each completed change."), output));

        Appointment saved = dataStore.saved.appointments().get(0);
        assertAll(
                () -> assertEquals(TriagePriority.URGENT, saved.getPriority()),
                () -> assertEquals(AppointmentStatus.CANCELLED, saved.getStatus()));
    }

    @Test
    void businessRuleErrorsAreShownAndTheMenuContinues() {
        String output = run(
                REGISTER_OWNER_AND_ANIMAL,
                "3", "1", "1", "2026-09-25 09:00", "30", "1", "", "", // first appointment
                "3", "1", "1", "2026-09-25 09:15", "30", "1", "", "", // overlaps the first one
                "3", "1", "1", "2206-01-01 10:00", "30", "1", "", "", // year typo
                "0");

        assertAll(
                () -> assertTrue(output.contains("Could not complete the operation: Schedule conflict"), output),
                () -> assertTrue(output.contains("Appointment must be within 2 years from now."), output),
                () -> assertTrue(output.contains("Goodbye."), output),
                () -> assertEquals(1, dataStore.saved.appointments().size()));
    }

    @Test
    void invalidMenuInputAndMissingRecordsAreHandled() {
        String output = run(
                "abc",      // not a number
                "99",       // outside the menu
                "3",        // schedule without animals
                "14",       // cancel without appointments
                "0");

        assertAll(
                () -> assertTrue(output.contains("Enter a whole number from 0 to 24."), output),
                () -> assertTrue(output.contains("No animals are registered yet."), output),
                () -> assertTrue(output.contains("There are no scheduled appointments available for this action."), output),
                () -> assertTrue(output.contains("Goodbye."), output));
    }

    @Test
    void cancellationCanBeAborted() {
        String output = run(
                REGISTER_OWNER_AND_ANIMAL,
                "3", "1", "1", "2026-09-25 09:00", "30", "1", "", "",
                "14", "1", "no",
                "0");

        assertAll(
                () -> assertTrue(output.contains("Cancellation aborted. No changes were made."), output),
                () -> assertEquals(AppointmentStatus.SCHEDULED,
                        dataStore.saved.appointments().get(0).getStatus()));
    }

    @Test
    void failedSaveIsReportedAndRememberedAtExit() {
        dataStore.failSaves = true;

        String output = run("1", "Test Owner", "+1 202-555-0100", "", "0");

        assertAll(
                () -> assertTrue(output.contains("Persistence error: Simulated disk failure."), output),
                () -> assertTrue(output.contains("Warning: at least one change could not be saved"), output));
    }

    @Test
    void closedInputEndsTheSessionSafely() {
        String output = run("1", "Test Owner");

        assertTrue(output.contains("Input closed. VetCare Scheduler is shutting down safely."), output);
    }

    @Test
    void differentSchedulesCanBeBookedAtTheSameTime() {
        String output = run(
                "17", "Dr. Ana",
                "17", "Dr. Bruno",
                "1", "Test Owner", "+1 202-555-0100", "",
                "2", "1", "Luna", "1", "2021-05-10", "",
                "3", "1", "1", "1", "2026-09-25 14:00", "30", "1", "", "",   // Dr. Ana, 14:00
                "3", "1", "2", "1", "2026-09-25 14:00", "30", "1", "", "",   // Dr. Bruno, 14:00
                "3", "1", "1", "1", "2026-09-25 14:15", "30", "1", "", "",   // Dr. Ana again: conflict
                "19", "1", "2026-09-25",                                      // view Dr. Ana
                "0");

        assertAll(
                () -> assertEquals(2, countOccurrences(output, "Appointment scheduled successfully")),
                () -> assertTrue(output.contains("Could not complete the operation: Schedule conflict"), output),
                () -> assertTrue(output.contains("Schedule Dr. Ana on 2026-09-25 (1)"), output),
                () -> assertEquals(2, dataStore.saved.appointments().size()));
    }

    @Test
    void schedulingWithoutAnyScheduleExplainsWhatToDo() {
        String output = run(
                "1", "Test Owner", "+1 202-555-0100", "",
                "2", "1", "Luna", "1", "2021-05-10", "",
                "3", "1",
                "0");

        assertTrue(output.contains("Register a schedule (option 17) before scheduling an appointment."), output);
    }

    @Test
    void attendanceCannotBeRecordedForAFutureAppointment() {
        String output = run(
                REGISTER_OWNER_AND_ANIMAL,
                "3", "1", "1", "2026-09-25 09:00", "30", "1", "", "",
                "20", "1", "1",                                     // record attendance: Completed
                "0");

        assertAll(
                () -> assertTrue(output.contains("Attendance can only be recorded after the appointment has started."), output),
                () -> assertEquals(AppointmentStatus.SCHEDULED, dataStore.saved.appointments().get(0).getStatus()));
    }

    @Test
    void deletingAnOwnerRequiresTheExactConfirmationWord() {
        String output = run(
                REGISTER_OWNER_AND_ANIMAL,
                "21", "1", "yes",                                   // wrong word: nothing changes
                "21", "1", "DELETE",
                "4",
                "0");

        assertAll(
                () -> assertTrue(output.contains("This permanently deletes the owner, 1 animal(s) and 0 appointment(s)."), output),
                () -> assertTrue(output.contains("Nothing was changed."), output),
                () -> assertTrue(output.contains("Owner and related records deleted."), output),
                () -> assertTrue(output.contains("Registered owners (0)"), output),
                () -> assertTrue(dataStore.saved.animals().isEmpty()));
    }

    @Test
    void ownerWithScheduledAppointmentsIsRefusedBeforeAskingForConfirmation() {
        String output = run(
                REGISTER_OWNER_AND_ANIMAL,
                "3", "1", "1", "2026-09-25 09:00", "30", "1", "", "",
                "21", "1",
                "22", "1",
                "23", "1",
                "0");

        assertAll(
                () -> assertEquals(3, countOccurrences(output, "There are 1 scheduled appointment(s)."), output),
                () -> assertFalse(output.contains("Type DELETE to confirm"), output),
                () -> assertFalse(output.contains("Type ANONYMIZE to confirm"), output),
                () -> assertEquals(1, dataStore.saved.owners().size()));
    }

    @Test
    void anonymizingAnOwnerKeepsTheRecordWithoutPersonalData() {
        String output = run(
                REGISTER_OWNER_AND_ANIMAL,
                "22", "1", "ANONYMIZE",
                "4",
                "0");

        assertAll(
                () -> assertTrue(output.contains("Owner anonymized."), output),
                () -> assertTrue(output.contains("Anonymized owner | phone:"), output),
                () -> assertFalse(output.substring(output.indexOf("Owner anonymized.")).contains("Test Owner"), output),
                () -> assertTrue(dataStore.saved.owners().get(0).isAnonymized()));
    }

    @Test
    void failedDeletionIsUndoneAndNotReportedAsAChangeInMemory() {
        run(REGISTER_OWNER_AND_ANIMAL, "0");
        dataStore.failSaves = true;

        String output = run("21", "1", "DELETE", "4", "0");
        String afterError = output.substring(output.indexOf("Persistence error"));

        assertAll(
                () -> assertTrue(afterError.contains("The change was undone, so nothing was modified."), output),
                () -> assertFalse(output.contains("may exist only in memory"), output),
                () -> assertTrue(afterError.contains("Test Owner"), output),
                () -> assertTrue(output.contains("Clinic data was saved after each completed change."), output));
    }

    private static int countOccurrences(String text, String fragment) {
        int count = 0;
        for (int index = text.indexOf(fragment); index >= 0; index = text.indexOf(fragment, index + 1)) {
            count++;
        }
        return count;
    }

    private String run(Object... inputs) {
        StringBuilder lines = new StringBuilder();
        for (Object input : inputs) {
            if (input instanceof String[] block) {
                for (String line : block) {
                    lines.append(line).append('\n');
                }
            } else {
                lines.append(input).append('\n');
            }
        }

        ClinicRepository repository = new ClinicRepository();
        PersistenceCoordinator persistence = new PersistenceCoordinator(repository, dataStore);
        persistence.load();
        OwnerService ownerService = new OwnerService(repository, persistence);
        AnimalService animalService = new AnimalService(repository, persistence, CLOCK);
        AppointmentService appointmentService = new AppointmentService(
                repository, persistence, new TriageService(), new ScheduleConflictService(), CLOCK);
        StringWriter output = new StringWriter();

        new VetCareCli(
                ownerService,
                animalService,
                appointmentService,
                new ReportService(repository),
                new ScheduleResourceService(repository, persistence),
                new PrivacyService(repository, persistence),
                CLOCK,
                new StringReader(lines.toString()),
                new PrintWriter(output, true))
                .run();
        return output.toString();
    }

    private static final class InMemoryDataStore implements ClinicDataStore {
        private ClinicSnapshot saved = ClinicSnapshot.empty();
        private boolean failSaves;

        @Override
        public ClinicSnapshot load() {
            return saved;
        }

        @Override
        public void save(ClinicSnapshot snapshot) {
            if (failSaves) {
                throw new DataPersistenceException("Simulated disk failure.", null);
            }
            saved = snapshot;
        }
    }
}
