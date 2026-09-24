package com.amandadamata.vetcare.service;

import com.amandadamata.vetcare.exception.ValidationException;
import com.amandadamata.vetcare.model.Appointment;
import com.amandadamata.vetcare.model.AppointmentStatus;
import com.amandadamata.vetcare.model.AppointmentType;
import com.amandadamata.vetcare.model.PatientCondition;
import com.amandadamata.vetcare.model.TriagePriority;
import com.amandadamata.vetcare.repository.ClinicRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReportServiceTest {
    private static final LocalDate DATE = LocalDate.of(2026, 9, 25);

    @Test
    void dailySummaryCountsStatusesPrioritiesAndTypesForRequestedDate() {
        ClinicRepository repository = new ClinicRepository();
        repository.saveAppointment(appointment(1, DATE.atTime(9, 0), AppointmentStatus.SCHEDULED,
                AppointmentType.CONSULTATION, TriagePriority.EMERGENCY));
        repository.saveAppointment(appointment(2, DATE.atTime(10, 0), AppointmentStatus.SCHEDULED,
                AppointmentType.CONSULTATION, TriagePriority.URGENT));
        repository.saveAppointment(appointment(3, DATE.atTime(11, 0), AppointmentStatus.CANCELLED,
                AppointmentType.EXAM, TriagePriority.EMERGENCY));
        repository.saveAppointment(appointment(4, DATE.plusDays(1).atTime(9, 0), AppointmentStatus.SCHEDULED,
                AppointmentType.VACCINATION, TriagePriority.STANDARD));

        DailySummary summary = new ReportService(repository).dailySummary(DATE);

        assertAll(
                () -> assertEquals(DATE, summary.date()),
                () -> assertEquals(3, summary.total()),
                () -> assertEquals(2, summary.scheduled()),
                () -> assertEquals(1, summary.cancelled()),
                () -> assertEquals(2L, summary.byPriority().get(TriagePriority.EMERGENCY)),
                () -> assertEquals(1L, summary.byPriority().get(TriagePriority.URGENT)),
                () -> assertEquals(0L, summary.byPriority().get(TriagePriority.STANDARD)),
                () -> assertEquals(2L, summary.byType().get(AppointmentType.CONSULTATION)),
                () -> assertEquals(1L, summary.byType().get(AppointmentType.EXAM)),
                () -> assertEquals(0L, summary.byType().get(AppointmentType.VACCINATION)),
                () -> assertEquals(0L, summary.byType().get(AppointmentType.FOLLOW_UP)));
    }

    @Test
    void emptyDateHasAllEnumKeysInitializedWithZero() {
        DailySummary summary = new ReportService(new ClinicRepository()).dailySummary(DATE);

        assertEquals(0, summary.total());
        assertEquals(Set.of(TriagePriority.values()), summary.byPriority().keySet());
        assertEquals(Set.of(AppointmentType.values()), summary.byType().keySet());
        assertEquals(List.of(0L, 0L, 0L),
                List.of(
                        summary.byPriority().get(TriagePriority.EMERGENCY),
                        summary.byPriority().get(TriagePriority.URGENT),
                        summary.byPriority().get(TriagePriority.STANDARD)));
    }

    @Test
    void dailySummaryRequiresDate() {
        assertThrows(ValidationException.class,
                () -> new ReportService(new ClinicRepository()).dailySummary(null));
    }

    private Appointment appointment(
            int suffix,
            LocalDateTime start,
            AppointmentStatus status,
            AppointmentType type,
            TriagePriority priority) {
        UUID id = UUID.fromString("00000000-0000-0000-0000-" + String.format("%012d", suffix));
        return new Appointment(
                id,
                UUID.fromString("10000000-0000-0000-0000-000000000001"),
                type,
                start,
                30,
                PatientCondition.STABLE,
                Set.of(),
                "",
                priority,
                status,
                Instant.parse("2026-09-24T13:00:00Z"));
    }
}
