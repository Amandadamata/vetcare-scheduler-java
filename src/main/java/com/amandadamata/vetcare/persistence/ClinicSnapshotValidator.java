package com.amandadamata.vetcare.persistence;

import com.amandadamata.vetcare.exception.ValidationException;
import com.amandadamata.vetcare.model.Animal;
import com.amandadamata.vetcare.model.Appointment;
import com.amandadamata.vetcare.model.Owner;
import com.amandadamata.vetcare.util.Validators;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Checks data read from disk before it reaches the repository.
 *
 * <p>Gson creates objects through their private no-argument constructors, so loaded data never
 * passes through the service validations. Without this check, an edited or damaged file could load
 * an unknown enum value as {@code null}, an out-of-range duration, or an appointment for an animal
 * that does not exist, and the application would fail later in an unrelated place.
 */
final class ClinicSnapshotValidator {
    private ClinicSnapshotValidator() {
    }

    static void validate(ClinicSnapshot snapshot) {
        Set<UUID> ownerIds = new HashSet<>();
        for (Owner owner : snapshot.owners()) {
            String context = "Owner " + owner.getId();
            requireUniqueId(owner.getId(), ownerIds, "Owner");
            check(context, () -> {
                Validators.requiredText(owner.getName(), "Owner name", 100);
                Validators.phone(owner.getPhone());
                requirePresent(owner.getEmail(), "email (use \"\" when there is none)");
                Validators.email(owner.getEmail());
            });
        }

        Set<UUID> animalIds = new HashSet<>();
        for (Animal animal : snapshot.animals()) {
            String context = "Animal " + animal.getId();
            requireUniqueId(animal.getId(), animalIds, "Animal");
            check(context, () -> {
                requireReference(animal.getOwnerId(), ownerIds, "owner");
                Validators.requiredText(animal.getName(), "Animal name", 100);
                requirePresent(animal.getSpecies(), "species");
                requirePresent(animal.getBirthDate(), "birth date");
                requirePresent(animal.getNotes(), "notes (use \"\" when there are none)");
                Validators.optionalText(animal.getNotes(), "Notes", 500);
            });
        }

        Set<UUID> appointmentIds = new HashSet<>();
        for (Appointment appointment : snapshot.appointments()) {
            String context = "Appointment " + appointment.getId();
            requireUniqueId(appointment.getId(), appointmentIds, "Appointment");
            check(context, () -> {
                requireReference(appointment.getAnimalId(), animalIds, "animal");
                requirePresent(appointment.getType(), "type");
                requirePresent(appointment.getStartTime(), "start time");
                Validators.duration(appointment.getDurationMinutes());
                requirePresent(appointment.getCondition(), "patient condition");
                requirePresent(appointment.getPriority(), "triage priority");
                requirePresent(appointment.getStatus(), "status");
                requirePresent(appointment.getCreatedAt(), "creation time");
                requireValidSigns(appointment);
                requirePresent(appointment.getNotes(), "notes (use \"\" when there are none)");
                Validators.optionalText(appointment.getNotes(), "Notes", 500);
            });
        }
    }

    private static void requireUniqueId(UUID id, Set<UUID> seenIds, String recordType) {
        if (id == null) {
            throw new ValidationException(recordType + " record is missing its ID.");
        }
        if (!seenIds.add(id)) {
            throw new ValidationException(recordType + " ID " + id + " appears more than once.");
        }
    }

    private static void requireReference(UUID id, Set<UUID> knownIds, String referenceName) {
        if (id == null || !knownIds.contains(id)) {
            throw new ValidationException("refers to a missing " + referenceName + " (" + id + ").");
        }
    }

    private static void requirePresent(Object value, String fieldName) {
        if (value == null) {
            throw new ValidationException("has a missing or unknown " + fieldName + ".");
        }
    }

    private static void requireValidSigns(Appointment appointment) {
        // Gson reads an unknown enum name as null. getClinicalSigns() copies the set with
        // Set.copyOf, which throws NullPointerException for a null set or a null element, so that
        // exception is translated into a message the user can act on.
        try {
            appointment.getClinicalSigns();
        } catch (NullPointerException exception) {
            throw new ValidationException("has a missing or unknown clinical sign.");
        }
    }

    private static void check(String context, Runnable validation) {
        try {
            validation.run();
        } catch (ValidationException exception) {
            throw new ValidationException(context + " " + describe(exception.getMessage()));
        }
    }

    private static String describe(String message) {
        if (message.startsWith("refers") || message.startsWith("has")) {
            return message;
        }
        return "is invalid: " + message;
    }
}
