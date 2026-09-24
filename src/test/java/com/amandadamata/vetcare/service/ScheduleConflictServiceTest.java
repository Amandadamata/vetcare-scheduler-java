package com.amandadamata.vetcare.service;

import com.amandadamata.vetcare.exception.ScheduleConflictException;
import com.amandadamata.vetcare.model.Appointment;
import com.amandadamata.vetcare.model.AppointmentStatus;
import com.amandadamata.vetcare.model.AppointmentType;
import com.amandadamata.vetcare.model.PatientCondition;
import com.amandadamata.vetcare.model.TriagePriority;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduleConflictServiceTest {
    private static final LocalDateTime TEN_OCLOCK = LocalDateTime.of(2026, 9, 25, 10, 0);

    private final ScheduleConflictService service = new ScheduleConflictService();

    @Test
    void detectsEveryKindOfRealOverlapButNotAdjacentIntervals() {
        LocalDateTime eleven = TEN_OCLOCK.plusHours(1);

        assertAll(
                () -> assertTrue(service.overlaps(TEN_OCLOCK, eleven,
                        TEN_OCLOCK.plusMinutes(30), eleven.plusMinutes(30))),
                () -> assertTrue(service.overlaps(TEN_OCLOCK, eleven,
                        TEN_OCLOCK.minusMinutes(30), TEN_OCLOCK.plusMinutes(30))),
                () -> assertTrue(service.overlaps(TEN_OCLOCK, eleven,
                        TEN_OCLOCK, eleven)),
                () -> assertFalse(service.overlaps(TEN_OCLOCK, eleven,
                        eleven, eleven.plusHours(1))),
                () -> assertFalse(service.overlaps(TEN_OCLOCK, eleven,
                        TEN_OCLOCK.minusHours(1), TEN_OCLOCK)));
    }

    @Test
    void rejectsOverlapWithScheduledAppointment() {
        Appointment existing = appointment(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                TEN_OCLOCK,
                AppointmentStatus.SCHEDULED);

        assertThrows(ScheduleConflictException.class,
                () -> service.ensureAvailable(List.of(existing), TEN_OCLOCK.plusMinutes(15), 30, null));
    }

    @Test
    void allowsAdjacentCancelledAndExplicitlyIgnoredAppointments() {
        UUID existingId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        Appointment scheduled = appointment(existingId, TEN_OCLOCK, AppointmentStatus.SCHEDULED);
        Appointment cancelled = appointment(
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                TEN_OCLOCK.plusHours(2),
                AppointmentStatus.CANCELLED);

        assertAll(
                () -> assertDoesNotThrow(() -> service.ensureAvailable(
                        List.of(scheduled), TEN_OCLOCK.plusMinutes(30), 30, null)),
                () -> assertDoesNotThrow(() -> service.ensureAvailable(
                        List.of(cancelled), TEN_OCLOCK.plusHours(2), 30, null)),
                () -> assertDoesNotThrow(() -> service.ensureAvailable(
                        List.of(scheduled), TEN_OCLOCK, 30, existingId)));
    }

    private Appointment appointment(UUID id, LocalDateTime start, AppointmentStatus status) {
        return new Appointment(
                id,
                UUID.fromString("10000000-0000-0000-0000-000000000001"),
                AppointmentType.CONSULTATION,
                start,
                30,
                PatientCondition.STABLE,
                Set.of(),
                "",
                TriagePriority.STANDARD,
                status,
                Instant.parse("2026-09-24T13:00:00Z"));
    }
}
