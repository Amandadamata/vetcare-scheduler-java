package com.amandadamata.vetcare.service;

import com.amandadamata.vetcare.exception.NotFoundException;
import com.amandadamata.vetcare.exception.ValidationException;
import com.amandadamata.vetcare.model.Animal;
import com.amandadamata.vetcare.model.Appointment;
import com.amandadamata.vetcare.model.AppointmentStatus;
import com.amandadamata.vetcare.model.Owner;
import com.amandadamata.vetcare.persistence.ClinicSnapshot;
import com.amandadamata.vetcare.persistence.PersistenceCoordinator;
import com.amandadamata.vetcare.repository.ClinicRepository;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Removal of personal data, in two explicit forms.
 *
 * <ul>
 *   <li><b>Deletion</b> removes the owner, their animals, and every appointment of those animals.
 *       Nothing is kept.</li>
 *   <li><b>Anonymization</b> keeps the appointment history (dates, types, priorities, statuses) for
 *       clinic statistics, but replaces the owner's name, phone and email, the animals' names, and
 *       every free-text note, because notes may contain personal data.</li>
 * </ul>
 *
 * Both are refused while any appointment of the owner's animals is still scheduled: the clinic
 * must cancel or record those appointments first, so no active booking disappears silently.
 * Records are removed or replaced as a whole, which keeps every reference valid: no animal points
 * to a missing owner and no appointment points to a missing animal. If saving fails, the
 * repository is restored, so memory and file stay the same.
 */
public class PrivacyService {
    private final ClinicRepository repository;
    private final PersistenceCoordinator persistence;

    public PrivacyService(ClinicRepository repository, PersistenceCoordinator persistence) {
        this.repository = repository;
        this.persistence = persistence;
    }

    /** What a deletion or anonymization would affect; shown to the user before confirming. */
    public record Impact(int animals, int appointments) {
    }

    public Impact impactOfOwner(UUID ownerId) {
        requireOwner(ownerId);
        Set<UUID> animalIds = animalIdsOf(ownerId);
        return new Impact(animalIds.size(), appointmentsOf(animalIds).size());
    }

    public Impact impactOfAnimal(UUID animalId) {
        requireAnimal(animalId);
        return new Impact(1, appointmentsOf(Set.of(animalId)).size());
    }

    /** Checks only the rules, so the CLI can refuse before asking for a confirmation word. */
    public void ensureOwnerCanBeDeleted(UUID ownerId) {
        requireOwner(ownerId);
        rejectScheduled(appointmentsOf(animalIdsOf(ownerId)));
    }

    public void ensureAnimalCanBeDeleted(UUID animalId) {
        requireAnimal(animalId);
        rejectScheduled(appointmentsOf(Set.of(animalId)));
    }

    public void ensureOwnerCanBeAnonymized(UUID ownerId) {
        if (requireOwner(ownerId).isAnonymized()) {
            throw new ValidationException("This owner is already anonymized.");
        }
        rejectScheduled(appointmentsOf(animalIdsOf(ownerId)));
    }

    public void deleteOwnerAndRecords(UUID ownerId) {
        ensureOwnerCanBeDeleted(ownerId);
        Set<UUID> animalIds = animalIdsOf(ownerId);
        List<Appointment> appointments = appointmentsOf(animalIds);

        ClinicSnapshot before = repository.snapshot();
        appointments.forEach(appointment -> repository.deleteAppointment(appointment.getId()));
        animalIds.forEach(repository::deleteAnimal);
        repository.deleteOwner(ownerId);
        persistence.saveOrRestore(before);
    }

    public void deleteAnimalAndRecords(UUID animalId) {
        ensureAnimalCanBeDeleted(animalId);
        List<Appointment> appointments = appointmentsOf(Set.of(animalId));

        ClinicSnapshot before = repository.snapshot();
        appointments.forEach(appointment -> repository.deleteAppointment(appointment.getId()));
        repository.deleteAnimal(animalId);
        persistence.saveOrRestore(before);
    }

    public void anonymizeOwner(UUID ownerId) {
        ensureOwnerCanBeAnonymized(ownerId);
        Owner owner = requireOwner(ownerId);
        Set<UUID> animalIds = animalIdsOf(ownerId);
        List<Appointment> appointments = appointmentsOf(animalIds);

        ClinicSnapshot before = repository.snapshot();
        repository.saveOwner(owner.anonymizedCopy());
        animalIds.forEach(id -> repository.saveAnimal(repository.findAnimal(id).orElseThrow().anonymizedCopy()));
        appointments.forEach(appointment -> repository.saveAppointment(appointment.withoutNotes()));
        persistence.saveOrRestore(before);
    }

    private Owner requireOwner(UUID ownerId) {
        return repository.findOwner(ownerId)
                .orElseThrow(() -> new NotFoundException("Owner not found: " + ownerId));
    }

    private void requireAnimal(UUID animalId) {
        if (repository.findAnimal(animalId).isEmpty()) {
            throw new NotFoundException("Animal not found: " + animalId);
        }
    }

    private Set<UUID> animalIdsOf(UUID ownerId) {
        return repository.allAnimals().stream()
                .filter(animal -> animal.getOwnerId().equals(ownerId))
                .map(Animal::getId)
                .collect(Collectors.toSet());
    }

    private List<Appointment> appointmentsOf(Set<UUID> animalIds) {
        return repository.allAppointments().stream()
                .filter(appointment -> animalIds.contains(appointment.getAnimalId()))
                .toList();
    }

    private static void rejectScheduled(List<Appointment> appointments) {
        long scheduled = appointments.stream()
                .filter(appointment -> appointment.getStatus() == AppointmentStatus.SCHEDULED)
                .count();
        if (scheduled > 0) {
            throw new ValidationException("There are " + scheduled + " scheduled appointment(s). "
                    + "Cancel them or record their outcome first.");
        }
    }
}
