package com.amandadamata.vetcare.service;

import com.amandadamata.vetcare.exception.NotFoundException;
import com.amandadamata.vetcare.exception.ScheduleConflictException;
import com.amandadamata.vetcare.exception.ValidationException;
import com.amandadamata.vetcare.model.Animal;
import com.amandadamata.vetcare.model.Appointment;
import com.amandadamata.vetcare.model.AppointmentStatus;
import com.amandadamata.vetcare.model.AppointmentType;
import com.amandadamata.vetcare.model.ClinicalSign;
import com.amandadamata.vetcare.model.PatientCondition;
import com.amandadamata.vetcare.model.ScheduleResource;
import com.amandadamata.vetcare.model.Species;
import com.amandadamata.vetcare.model.TriagePriority;
import com.amandadamata.vetcare.persistence.ClinicDataStore;
import com.amandadamata.vetcare.persistence.ClinicSnapshot;
import com.amandadamata.vetcare.persistence.PersistenceCoordinator;
import com.amandadamata.vetcare.repository.ClinicRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AppointmentServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-24T13:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneId.of("America/Sao_Paulo"));
    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 9, 25);
    /** NOW expressed in the clock's zone (America/Sao_Paulo, UTC-3): 2026-09-24 10:00. */
    private static final LocalDateTime NOW_LOCAL = LocalDateTime.of(2026, 9, 24, 10, 0);
    private static final UUID ANIMAL_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID RESOURCE_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");

    private ClinicRepository repository;
    private RecordingDataStore dataStore;
    private AppointmentService service;

    @BeforeEach
    void setUp() {
        repository = new ClinicRepository();
        repository.saveAnimal(new Animal(
                ANIMAL_ID,
                UUID.fromString("20000000-0000-0000-0000-000000000001"),
                "Luna",
                Species.DOG,
                LocalDate.of(2021, 5, 10),
                ""));
        repository.saveResource(new ScheduleResource(RESOURCE_ID, "Dr. Ana"));
        dataStore = new RecordingDataStore();
        service = new AppointmentService(
                repository,
                new PersistenceCoordinator(repository, dataStore),
                new TriageService(),
                new ScheduleConflictService(),
                CLOCK);
    }

    @Test
    void scheduleCalculatesPriorityCopiesSignsAndPersistsTheAppointment() {
        Set<ClinicalSign> suppliedSigns = new HashSet<>(Set.of(ClinicalSign.SEVERE_PAIN));

        Appointment scheduled = service.schedule(
                ANIMAL_ID,
                RESOURCE_ID,
                AppointmentType.CONSULTATION,
                TARGET_DATE.atTime(9, 0),
                45,
                PatientCondition.STABLE,
                suppliedSigns,
                "  Needs observation  ");
        suppliedSigns.clear();

        assertAll(
                () -> assertEquals(TriagePriority.URGENT, scheduled.getPriority()),
                () -> assertEquals(Set.of(ClinicalSign.SEVERE_PAIN), scheduled.getClinicalSigns()),
                () -> assertEquals("Needs observation", scheduled.getNotes()),
                () -> assertEquals(AppointmentStatus.SCHEDULED, scheduled.getStatus()),
                () -> assertEquals(NOW, scheduled.getCreatedAt()),
                () -> assertEquals(1, dataStore.saveCount),
                () -> assertEquals(scheduled.getId(), dataStore.snapshot.appointments().get(0).getId()));
    }

    @Test
    void scheduleAcceptsAnImmutableSetOfClinicalSigns() {
        Appointment scheduled = service.schedule(
                ANIMAL_ID,
                RESOURCE_ID,
                AppointmentType.CONSULTATION,
                TARGET_DATE.atTime(10, 30),
                30,
                PatientCondition.STABLE,
                Set.of(ClinicalSign.SEVERE_PAIN),
                "");

        assertEquals(TriagePriority.URGENT, scheduled.getPriority());
        assertEquals(Set.of(ClinicalSign.SEVERE_PAIN), scheduled.getClinicalSigns());
    }

    @Test
    void scheduleRejectsConflictWithoutPersistingAnotherAppointment() {
        service.schedule(
                ANIMAL_ID,
                RESOURCE_ID,
                AppointmentType.CONSULTATION,
                TARGET_DATE.atTime(9, 0),
                60,
                PatientCondition.STABLE,
                Set.of(),
                "");

        assertThrows(ScheduleConflictException.class, () -> service.schedule(
                ANIMAL_ID,
                RESOURCE_ID,
                AppointmentType.EXAM,
                TARGET_DATE.atTime(9, 30),
                30,
                PatientCondition.STABLE,
                Set.of(),
                ""));
        assertEquals(1, dataStore.saveCount);
        assertEquals(1, repository.allAppointments().size());
    }

    @Test
    void listUsesChronologicalOrderAndIdAsTieBreaker() {
        Appointment later = appointment(id(3), TARGET_DATE.atTime(11, 0), AppointmentStatus.SCHEDULED,
                AppointmentType.EXAM, TriagePriority.STANDARD, NOW);
        Appointment secondAtNine = appointment(id(2), TARGET_DATE.atTime(9, 0), AppointmentStatus.SCHEDULED,
                AppointmentType.CONSULTATION, TriagePriority.STANDARD, NOW);
        Appointment firstAtNine = appointment(id(1), TARGET_DATE.atTime(9, 0), AppointmentStatus.SCHEDULED,
                AppointmentType.VACCINATION, TriagePriority.STANDARD, NOW);
        repository.saveAppointment(later);
        repository.saveAppointment(secondAtNine);
        repository.saveAppointment(firstAtNine);

        assertEquals(List.of(id(1), id(2), id(3)), ids(service.list(null)));
    }

    @Test
    void listAppliesDateStatusTypeAndPriorityFilters() {
        Appointment match = appointment(id(1), TARGET_DATE.atTime(9, 0), AppointmentStatus.SCHEDULED,
                AppointmentType.CONSULTATION, TriagePriority.EMERGENCY, NOW);
        Appointment wrongStatus = appointment(id(2), TARGET_DATE.atTime(10, 0), AppointmentStatus.CANCELLED,
                AppointmentType.CONSULTATION, TriagePriority.EMERGENCY, NOW);
        Appointment wrongType = appointment(id(3), TARGET_DATE.atTime(11, 0), AppointmentStatus.SCHEDULED,
                AppointmentType.EXAM, TriagePriority.EMERGENCY, NOW);
        Appointment wrongPriority = appointment(id(4), TARGET_DATE.atTime(12, 0), AppointmentStatus.SCHEDULED,
                AppointmentType.CONSULTATION, TriagePriority.STANDARD, NOW);
        Appointment wrongDate = appointment(id(5), TARGET_DATE.plusDays(1).atTime(9, 0), AppointmentStatus.SCHEDULED,
                AppointmentType.CONSULTATION, TriagePriority.EMERGENCY, NOW);
        List.of(match, wrongStatus, wrongType, wrongPriority, wrongDate).forEach(repository::saveAppointment);

        AppointmentFilter filter = new AppointmentFilter(
                TARGET_DATE,
                AppointmentStatus.SCHEDULED,
                AppointmentType.CONSULTATION,
                TriagePriority.EMERGENCY,
                null);

        assertEquals(List.of(id(1)), ids(service.list(filter)));
        assertEquals(List.of(id(1), id(2), id(3), id(4)),
                ids(service.list(new AppointmentFilter(TARGET_DATE, null, null, null, null))));
        assertEquals(List.of(id(1), id(3), id(4), id(5)),
                ids(service.list(new AppointmentFilter(null, AppointmentStatus.SCHEDULED, null, null, null))));
        assertEquals(List.of(id(1), id(2), id(4), id(5)),
                ids(service.list(new AppointmentFilter(null, null, AppointmentType.CONSULTATION, null, null))));
        assertEquals(List.of(id(1), id(2), id(3), id(5)),
                ids(service.list(new AppointmentFilter(null, null, null, TriagePriority.EMERGENCY, null))));
    }

    @Test
    void triageQueueOrdersByPriorityThenCreationTimeAndExcludesCancelledOrOtherDates() {
        Appointment standard = appointment(id(1), TARGET_DATE.atTime(8, 0), AppointmentStatus.SCHEDULED,
                AppointmentType.CONSULTATION, TriagePriority.STANDARD, NOW.minusSeconds(600));
        Appointment urgent = appointment(id(2), TARGET_DATE.atTime(9, 0), AppointmentStatus.SCHEDULED,
                AppointmentType.CONSULTATION, TriagePriority.URGENT, NOW.minusSeconds(300));
        Appointment newerEmergency = appointment(id(3), TARGET_DATE.atTime(10, 0), AppointmentStatus.SCHEDULED,
                AppointmentType.CONSULTATION, TriagePriority.EMERGENCY, NOW.minusSeconds(60));
        Appointment olderEmergency = appointment(id(4), TARGET_DATE.atTime(11, 0), AppointmentStatus.SCHEDULED,
                AppointmentType.CONSULTATION, TriagePriority.EMERGENCY, NOW.minusSeconds(120));
        Appointment cancelledEmergency = appointment(id(5), TARGET_DATE.atTime(7, 0), AppointmentStatus.CANCELLED,
                AppointmentType.CONSULTATION, TriagePriority.EMERGENCY, NOW.minusSeconds(1_000));
        Appointment tomorrowEmergency = appointment(id(6), TARGET_DATE.plusDays(1).atTime(7, 0), AppointmentStatus.SCHEDULED,
                AppointmentType.CONSULTATION, TriagePriority.EMERGENCY, NOW.minusSeconds(1_000));
        List.of(standard, urgent, newerEmergency, olderEmergency, cancelledEmergency, tomorrowEmergency)
                .forEach(repository::saveAppointment);

        assertEquals(List.of(id(4), id(3), id(2), id(1)), ids(service.triageQueue(TARGET_DATE)));
        assertThrows(ValidationException.class, () -> service.triageQueue(null));
    }

    @Test
    void updateClinicalDetailsRecalculatesPriorityAndPersists() {
        Appointment appointment = appointment(id(1), TARGET_DATE.atTime(9, 0), AppointmentStatus.SCHEDULED,
                AppointmentType.CONSULTATION, TriagePriority.STANDARD, NOW);
        repository.saveAppointment(appointment);

        Appointment updated = service.updateClinicalDetails(
                appointment.getId(),
                AppointmentType.EXAM,
                PatientCondition.STABLE,
                Set.of(ClinicalSign.SEIZURE),
                "  Seizure reported by owner  ");

        assertAll(
                () -> assertEquals(TriagePriority.EMERGENCY, updated.getPriority()),
                () -> assertEquals(AppointmentType.EXAM, updated.getType()),
                () -> assertEquals(Set.of(ClinicalSign.SEIZURE), updated.getClinicalSigns()),
                () -> assertEquals("Seizure reported by owner", updated.getNotes()),
                () -> assertEquals(TARGET_DATE.atTime(9, 0), updated.getStartTime()),
                () -> assertEquals(1, dataStore.saveCount));
    }

    @Test
    void invalidClinicalUpdateChangesNothing() {
        Appointment appointment = appointment(id(1), TARGET_DATE.atTime(9, 0), AppointmentStatus.SCHEDULED,
                AppointmentType.CONSULTATION, TriagePriority.STANDARD, NOW);
        repository.saveAppointment(appointment);

        assertThrows(ValidationException.class, () -> service.updateClinicalDetails(
                appointment.getId(), AppointmentType.EXAM, null, Set.of(ClinicalSign.SEIZURE), ""));

        assertAll(
                () -> assertEquals(AppointmentType.CONSULTATION, appointment.getType()),
                () -> assertEquals(TriagePriority.STANDARD, appointment.getPriority()),
                () -> assertEquals(0, dataStore.saveCount));
    }

    @Test
    void cancelChangesStatusPersistsAndPreventsFurtherChanges() {
        Appointment appointment = appointment(id(1), TARGET_DATE.atTime(9, 0), AppointmentStatus.SCHEDULED,
                AppointmentType.CONSULTATION, TriagePriority.STANDARD, NOW);
        repository.saveAppointment(appointment);

        Appointment cancelled = service.cancel(appointment.getId());

        assertEquals(AppointmentStatus.CANCELLED, cancelled.getStatus());
        assertEquals(1, dataStore.saveCount);
        assertAll(
                () -> assertThrows(ValidationException.class, () -> service.cancel(appointment.getId())),
                () -> assertThrows(ValidationException.class, () -> service.reschedule(
                appointment.getId(), RESOURCE_ID, TARGET_DATE.atTime(12, 0), 30)),
                () -> assertThrows(ValidationException.class, () -> service.updateClinicalDetails(
                        appointment.getId(),
                        AppointmentType.EXAM,
                        PatientCondition.STABLE,
                        Set.of(),
                        "")));
        assertEquals(1, dataStore.saveCount);
    }

    @Test
    void rescheduleIgnoresItsOwnOldIntervalAndPersistsNewValues() {
        Appointment appointment = appointment(id(1), TARGET_DATE.atTime(9, 0), AppointmentStatus.SCHEDULED,
                AppointmentType.CONSULTATION, TriagePriority.STANDARD, NOW);
        repository.saveAppointment(appointment);

        Appointment rescheduled = service.reschedule(
                appointment.getId(), RESOURCE_ID, TARGET_DATE.atTime(9, 15), 60);

        assertAll(
                () -> assertEquals(TARGET_DATE.atTime(9, 15), rescheduled.getStartTime()),
                () -> assertEquals(60, rescheduled.getDurationMinutes()),
                () -> assertEquals(1, dataStore.saveCount));
    }

    @Test
    void failedRescheduleKeepsOriginalValuesAndDoesNotPersist() {
        Appointment target = appointment(id(1), TARGET_DATE.atTime(9, 0), AppointmentStatus.SCHEDULED,
                AppointmentType.CONSULTATION, TriagePriority.STANDARD, NOW);
        Appointment occupied = appointment(id(2), TARGET_DATE.atTime(11, 0), AppointmentStatus.SCHEDULED,
                AppointmentType.EXAM, TriagePriority.STANDARD, NOW);
        repository.saveAppointment(target);
        repository.saveAppointment(occupied);

        assertThrows(ScheduleConflictException.class,
                () -> service.reschedule(
                target.getId(), RESOURCE_ID, TARGET_DATE.atTime(11, 15), 30));
        assertAll(
                () -> assertEquals(TARGET_DATE.atTime(9, 0), target.getStartTime()),
                () -> assertEquals(30, target.getDurationMinutes()),
                () -> assertEquals(0, dataStore.saveCount));
    }

    @Test
    void sameTimeIsAllowedOnDifferentSchedulesButNotOnTheSameOne() {
        UUID otherSchedule = UUID.fromString("40000000-0000-0000-0000-000000000002");
        repository.saveResource(new ScheduleResource(otherSchedule, "Dr. Bruno"));

        Appointment withAna = scheduleAt(RESOURCE_ID, TARGET_DATE.atTime(14, 0));
        Appointment withBruno = scheduleAt(otherSchedule, TARGET_DATE.atTime(14, 0));

        assertAll(
                () -> assertEquals(RESOURCE_ID, withAna.getResourceId()),
                () -> assertEquals(otherSchedule, withBruno.getResourceId()),
                () -> assertThrows(ScheduleConflictException.class,
                        () -> scheduleAt(RESOURCE_ID, TARGET_DATE.atTime(14, 15))),
                () -> assertEquals(2, repository.allAppointments().size()));
    }

    @Test
    void schedulingRequiresAnExistingSchedule() {
        assertThrows(NotFoundException.class, () -> scheduleAt(UUID.randomUUID(), TARGET_DATE.atTime(14, 0)));
        assertThrows(NotFoundException.class, () -> scheduleAt(null, TARGET_DATE.atTime(14, 0)));
    }

    @Test
    void rescheduleCanMoveToAnotherScheduleAndChecksThatSchedule() {
        UUID otherSchedule = UUID.fromString("40000000-0000-0000-0000-000000000002");
        repository.saveResource(new ScheduleResource(otherSchedule, "Dr. Bruno"));
        Appointment moving = scheduleAt(RESOURCE_ID, TARGET_DATE.atTime(9, 0));
        scheduleAt(otherSchedule, TARGET_DATE.atTime(11, 0));

        Appointment moved = service.reschedule(moving.getId(), otherSchedule, TARGET_DATE.atTime(10, 0), 30);

        assertAll(
                () -> assertEquals(otherSchedule, moved.getResourceId()),
                () -> assertThrows(ScheduleConflictException.class,
                        () -> service.reschedule(moving.getId(), otherSchedule, TARGET_DATE.atTime(11, 0), 30)));
    }

    @Test
    void listCanBeFilteredBySchedule() {
        UUID otherSchedule = UUID.fromString("40000000-0000-0000-0000-000000000002");
        repository.saveResource(new ScheduleResource(otherSchedule, "Dr. Bruno"));
        Appointment withAna = scheduleAt(RESOURCE_ID, TARGET_DATE.atTime(9, 0));
        scheduleAt(otherSchedule, TARGET_DATE.atTime(9, 0));

        assertEquals(List.of(withAna.getId()),
                ids(service.list(AppointmentFilter.forResource(RESOURCE_ID, null))));
    }

    @Test
    void attendanceCanBeRecordedOnceTheAppointmentHasStarted() {
        Appointment started = appointment(id(1), NOW_LOCAL.minusMinutes(30), AppointmentStatus.SCHEDULED,
                AppointmentType.CONSULTATION, TriagePriority.STANDARD, NOW);
        Appointment alsoStarted = appointment(id(2), NOW_LOCAL.minusHours(3), AppointmentStatus.SCHEDULED,
                AppointmentType.CONSULTATION, TriagePriority.STANDARD, NOW);
        repository.saveAppointment(started);
        repository.saveAppointment(alsoStarted);

        service.changeStatus(started.getId(), AppointmentStatus.COMPLETED);
        service.changeStatus(alsoStarted.getId(), AppointmentStatus.NO_SHOW);

        assertAll(
                () -> assertEquals(AppointmentStatus.COMPLETED, started.getStatus()),
                () -> assertEquals(AppointmentStatus.NO_SHOW, alsoStarted.getStatus()),
                () -> assertEquals(2, dataStore.saveCount));
    }

    @Test
    void attendanceCannotBeRecordedBeforeTheAppointmentStarts() {
        Appointment future = scheduleAt(RESOURCE_ID, TARGET_DATE.atTime(9, 0));
        int savesBefore = dataStore.saveCount;

        ValidationException error = assertThrows(ValidationException.class,
                () -> service.changeStatus(future.getId(), AppointmentStatus.COMPLETED));

        assertAll(
                () -> assertEquals("Attendance can only be recorded after the appointment has started.", error.getMessage()),
                () -> assertEquals(AppointmentStatus.SCHEDULED, future.getStatus()),
                () -> assertEquals(savesBefore, dataStore.saveCount));
    }

    @Test
    void finalStatusesCannotChangeOrBeRescheduled() {
        Appointment completed = appointment(id(1), NOW_LOCAL.minusHours(1), AppointmentStatus.COMPLETED,
                AppointmentType.CONSULTATION, TriagePriority.STANDARD, NOW);
        repository.saveAppointment(completed);

        assertAll(
                () -> assertEquals("An appointment that is Completed cannot become Cancelled.",
                        assertThrows(ValidationException.class,
                                () -> service.changeStatus(completed.getId(), AppointmentStatus.CANCELLED)).getMessage()),
                () -> assertThrows(ValidationException.class,
                        () -> service.reschedule(completed.getId(), RESOURCE_ID, TARGET_DATE.atTime(9, 0), 30)),
                () -> assertThrows(ValidationException.class,
                        () -> service.changeStatus(completed.getId(), null)),
                () -> assertEquals(AppointmentStatus.COMPLETED, completed.getStatus()),
                () -> assertEquals(0, dataStore.saveCount));
    }

    @Test
    void noShowFreesTheRestOfTheSlotButCompletedDoesNot() {
        // Both appointments run from 9:45 to 10:15 (the helper uses 30 minutes); "now" is 10:00 local time.
        Appointment noShow = appointment(id(1), NOW_LOCAL.minusMinutes(15), AppointmentStatus.SCHEDULED,
                AppointmentType.CONSULTATION, TriagePriority.STANDARD, NOW);
        repository.saveAppointment(noShow);
        service.changeStatus(noShow.getId(), AppointmentStatus.NO_SHOW);

        Appointment reused = scheduleAt(RESOURCE_ID, NOW_LOCAL.plusMinutes(5));
        service.cancel(reused.getId());
        Appointment completed = appointment(id(2), NOW_LOCAL.minusMinutes(15), AppointmentStatus.COMPLETED,
                AppointmentType.CONSULTATION, TriagePriority.STANDARD, NOW);
        repository.saveAppointment(completed);

        assertThrows(ScheduleConflictException.class, () -> scheduleAt(RESOURCE_ID, NOW_LOCAL.plusMinutes(5)));
    }

    private Appointment scheduleAt(UUID resourceId, LocalDateTime start) {
        return service.schedule(ANIMAL_ID, resourceId, AppointmentType.CONSULTATION, start, 30,
                PatientCondition.STABLE, Set.of(), "");
    }

    private Appointment appointment(
            UUID id,
            LocalDateTime start,
            AppointmentStatus status,
            AppointmentType type,
            TriagePriority priority,
            Instant createdAt) {
        return new Appointment(
                id,
                ANIMAL_ID,
                RESOURCE_ID,
                type,
                start,
                30,
                PatientCondition.STABLE,
                Set.of(),
                "",
                priority,
                status,
                createdAt);
    }

    private UUID id(int suffix) {
        return UUID.fromString("00000000-0000-0000-0000-" + String.format("%012d", suffix));
    }

    private List<UUID> ids(List<Appointment> appointments) {
        return appointments.stream().map(Appointment::getId).toList();
    }

    private static final class RecordingDataStore implements ClinicDataStore {
        private ClinicSnapshot snapshot = ClinicSnapshot.empty();
        private int saveCount;

        @Override
        public ClinicSnapshot load() {
            return snapshot;
        }

        @Override
        public void save(ClinicSnapshot snapshot) {
            this.snapshot = snapshot;
            saveCount++;
        }
    }
}
