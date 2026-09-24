package com.amandadamata.vetcare.util;

import com.amandadamata.vetcare.exception.ValidationException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

public final class Validators {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private Validators() {
    }

    public static String requiredText(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(fieldName + " is required.");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new ValidationException(fieldName + " must have at most " + maxLength + " characters.");
        }
        return trimmed;
    }

    public static String optionalText(String value, String fieldName, int maxLength) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.length() > maxLength) {
            throw new ValidationException(fieldName + " must have at most " + maxLength + " characters.");
        }
        return trimmed;
    }

    public static String phone(String value) {
        String trimmed = requiredText(value, "Phone", 30);
        long digitCount = trimmed.chars().filter(Character::isDigit).count();
        if (digitCount < 8 || digitCount > 15) {
            throw new ValidationException("Phone must contain between 8 and 15 digits.");
        }
        return trimmed;
    }

    public static String email(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (!trimmed.isEmpty() && !EMAIL_PATTERN.matcher(trimmed).matches()) {
            throw new ValidationException("Email format is invalid.");
        }
        return trimmed;
    }

    public static LocalDate birthDate(LocalDate value, Clock clock) {
        if (value == null) {
            throw new ValidationException("Birth date is required.");
        }
        if (value.isAfter(LocalDate.now(clock))) {
            throw new ValidationException("Birth date cannot be in the future.");
        }
        return value;
    }

    public static LocalDateTime futureAppointment(LocalDateTime value, Clock clock) {
        if (value == null) {
            throw new ValidationException("Appointment date and time are required.");
        }
        if (!value.isAfter(LocalDateTime.now(clock))) {
            throw new ValidationException("Appointment must be scheduled in the future.");
        }
        return value;
    }

    public static int duration(int minutes) {
        if (minutes < 15 || minutes > 180) {
            throw new ValidationException("Duration must be between 15 and 180 minutes.");
        }
        return minutes;
    }
}

