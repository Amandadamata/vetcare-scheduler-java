package com.amandadamata.vetcare.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppointmentStatusTest {

    /** Every pair (from, to): only SCHEDULED -> COMPLETED / NO_SHOW / CANCELLED is allowed. */
    @ParameterizedTest(name = "{0} -> {1}: {2}")
    @CsvSource({
            "SCHEDULED, SCHEDULED, false",
            "SCHEDULED, COMPLETED, true",
            "SCHEDULED, NO_SHOW,   true",
            "SCHEDULED, CANCELLED, true",
            "COMPLETED, SCHEDULED, false",
            "COMPLETED, COMPLETED, false",
            "COMPLETED, NO_SHOW,   false",
            "COMPLETED, CANCELLED, false",
            "NO_SHOW,   SCHEDULED, false",
            "NO_SHOW,   COMPLETED, false",
            "NO_SHOW,   NO_SHOW,   false",
            "NO_SHOW,   CANCELLED, false",
            "CANCELLED, SCHEDULED, false",
            "CANCELLED, COMPLETED, false",
            "CANCELLED, NO_SHOW,   false",
            "CANCELLED, CANCELLED, false"})
    void transitionTable(AppointmentStatus from, AppointmentStatus to, boolean allowed) {
        assertEquals(allowed, from.canTransitionTo(to));
    }

    @Test
    void onlyScheduledAndCompletedOccupyTheSchedule() {
        assertAll(
                () -> assertTrue(AppointmentStatus.SCHEDULED.occupiesSchedule()),
                () -> assertTrue(AppointmentStatus.COMPLETED.occupiesSchedule()),
                () -> assertFalse(AppointmentStatus.CANCELLED.occupiesSchedule()),
                () -> assertFalse(AppointmentStatus.NO_SHOW.occupiesSchedule()));
    }

    @Test
    void onlyScheduledIsNotFinal() {
        assertAll(
                () -> assertFalse(AppointmentStatus.SCHEDULED.isFinal()),
                () -> assertTrue(AppointmentStatus.COMPLETED.isFinal()),
                () -> assertTrue(AppointmentStatus.CANCELLED.isFinal()),
                () -> assertTrue(AppointmentStatus.NO_SHOW.isFinal()));
    }
}
