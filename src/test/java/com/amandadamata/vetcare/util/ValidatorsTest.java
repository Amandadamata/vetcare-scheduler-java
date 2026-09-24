package com.amandadamata.vetcare.util;

import com.amandadamata.vetcare.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ValidatorsTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-24T13:00:00Z"),
            ZoneId.of("America/Sao_Paulo"));
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 24);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 24, 10, 0);

    @Test
    void requiredTextTrimsValidInputAndRejectsMissingOrLongValues() {
        assertEquals("Amanda", Validators.requiredText("  Amanda  ", "Name", 10));
        assertAll(
                () -> assertThrows(ValidationException.class,
                        () -> Validators.requiredText(null, "Name", 10)),
                () -> assertThrows(ValidationException.class,
                        () -> Validators.requiredText("   ", "Name", 10)),
                () -> assertThrows(ValidationException.class,
                        () -> Validators.requiredText("12345678901", "Name", 10)));
    }

    @Test
    void optionalTextNormalizesMissingInputAndEnforcesMaximumLength() {
        assertEquals("", Validators.optionalText(null, "Notes", 5));
        assertEquals("note", Validators.optionalText(" note ", "Notes", 5));
        assertThrows(ValidationException.class,
                () -> Validators.optionalText("123456", "Notes", 5));
    }

    @Test
    void phoneAcceptsEightToFifteenDigitsAndRejectsValuesOutsideThatRange() {
        assertEquals("(62) 99914-4445", Validators.phone("  (62) 99914-4445 "));
        assertEquals("12345678", Validators.phone("12345678"));
        assertEquals("123456789012345", Validators.phone("123456789012345"));
        assertAll(
                () -> assertThrows(ValidationException.class, () -> Validators.phone("1234567")),
                () -> assertThrows(ValidationException.class, () -> Validators.phone("1234567890123456")),
                () -> assertThrows(ValidationException.class, () -> Validators.phone(null)));
    }

    @Test
    void emailAcceptsBlankOrWellFormedValuesAndRejectsInvalidFormat() {
        assertEquals("", Validators.email(null));
        assertEquals("", Validators.email("   "));
        assertEquals("amanda@example.com", Validators.email(" amanda@example.com "));
        assertAll(
                () -> assertThrows(ValidationException.class, () -> Validators.email("amanda")),
                () -> assertThrows(ValidationException.class, () -> Validators.email("a @example.com")),
                () -> assertThrows(ValidationException.class, () -> Validators.email("a@example")));
    }

    @Test
    void birthDateUsesTheInjectedClockAndAllowsToday() {
        assertEquals(TODAY, Validators.birthDate(TODAY, CLOCK));
        assertEquals(TODAY.minusDays(1), Validators.birthDate(TODAY.minusDays(1), CLOCK));
        assertAll(
                () -> assertThrows(ValidationException.class,
                        () -> Validators.birthDate(TODAY.plusDays(1), CLOCK)),
                () -> assertThrows(ValidationException.class,
                        () -> Validators.birthDate(null, CLOCK)));
    }

    @Test
    void appointmentMustBeStrictlyAfterTheTimeFromTheInjectedClock() {
        assertEquals(NOW.plusMinutes(1), Validators.futureAppointment(NOW.plusMinutes(1), CLOCK));
        assertAll(
                () -> assertThrows(ValidationException.class,
                        () -> Validators.futureAppointment(NOW, CLOCK)),
                () -> assertThrows(ValidationException.class,
                        () -> Validators.futureAppointment(NOW.minusMinutes(1), CLOCK)),
                () -> assertThrows(ValidationException.class,
                        () -> Validators.futureAppointment(null, CLOCK)));
    }

    @Test
    void durationAcceptsInclusiveLimitsAndRejectsValuesOutsideThem() {
        assertEquals(15, Validators.duration(15));
        assertEquals(180, Validators.duration(180));
        assertAll(
                () -> assertThrows(ValidationException.class, () -> Validators.duration(14)),
                () -> assertThrows(ValidationException.class, () -> Validators.duration(181)));
    }
}
