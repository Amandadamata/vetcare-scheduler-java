package com.amandadamata.vetcare.service;

import com.amandadamata.vetcare.exception.NotFoundException;
import com.amandadamata.vetcare.exception.ValidationException;
import com.amandadamata.vetcare.model.Appointment;
import com.amandadamata.vetcare.model.AppointmentStatus;
import com.amandadamata.vetcare.model.AppointmentType;
import com.amandadamata.vetcare.model.ClinicalSign;
import com.amandadamata.vetcare.model.PatientCondition;
import com.amandadamata.vetcare.model.TriagePriority;
import com.amandadamata.vetcare.persistence.PersistenceCoordinator;
import com.amandadamata.vetcare.repository.ClinicRepository;
import com.amandadamata.vetcare.util.Validators;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

public class AppointmentService {
    private static final Comparator<Appointment> CHRONOLOGICAL_ORDER = Comparator
            .comparing(Appointment::getStartTime)
            .thenComparing(appointment -> appointment.getId().toString());

    private static final Comparator<Appointment> TRIAGE_ORDER = Comparator
            .comparingInt((Appointment appointment) -> appointment.getPriority().getRank())
            .thenComparing(Appointment::getCreatedAt)
            .thenComparing(Appointment::getStartTime)
            .thenComparing(appointment -> appointment.getId().toString());

    private final ClinicRepository repository;
    private final PersistenceCoordinator persistence;
    private final TriageService triageService;
    private final ScheduleConflictService conflictService;
    private final Clock clock;

    public AppointmentService(
            ClinicRepository repository,
            PersistenceCoordinator persistence,
            TriageService triageService,
            ScheduleConflictService conflictService,
            Clock clock) {
        this.repository = repository;
        this.persistence = persistence;
        this.triageService = triageService;
        this.conflictService = conflictService;
        this.clock = clock;
    }

    public Appointment schedule(
            UUID animalId,
            AppointmentType type,
            LocalDateTime startTime,
            int durationMinutes,
            PatientCondition condition,
            Set<ClinicalSign> clinicalSigns,
            String notes) {
        requireAnimal(animalId);
        AppointmentType validType = requireType(type);
        PatientCondition validCondition = requireCondition(condition);
        LocalDateTime validStart = Validators.futureAppointment(startTime, clock);
        int validDuration = Validators.duration(durationMinutes);
        Set<ClinicalSign> validSigns = copySigns(clinicalSigns);
        String validNotes = Validators.optionalText(notes, "Notes", 500);

        conflictService.ensureAvailable(repository.allAppointments(), validStart, validDuration, null);
        TriagePriority priority = triageService.calculate(validCondition, validSigns);
        Appointment appointment = new Appointment(
                UUID.randomUUID(),
                animalId,
                validType,
                validStart,
                validDuration,
                validCondition,
                validSigns,
                validNotes,
                priority,
                AppointmentStatus.SCHEDULED,
                clock.instant());
        repository.saveAppointment(appointment);
        persistence.save();
        return appointment;
    }

    public Appointment updateClinicalDetails(
            UUID id,
            AppointmentType type,
            PatientCondition condition,
            Set<ClinicalSign> clinicalSigns,
            String notes) {
        Appointment appointment = requireScheduled(id);
        AppointmentType validType = requireType(type);
        PatientCondition validCondition = requireCondition(condition);
        Set<ClinicalSign> validSigns = copySigns(clinicalSigns);
        appointment.updateClinicalDetails(
                validType,
                validCondition,
                validSigns,
                Validators.optionalText(notes, "Notes", 500),
                triageService.calculate(validCondition, validSigns));
        repository.saveAppointment(appointment);
        persistence.save();
        return appointment;
    }

    public Appointment reschedule(UUID id, LocalDateTime newStart, int newDurationMinutes) {
        Appointment appointment = requireScheduled(id);
        LocalDateTime validStart = Validators.futureAppointment(newStart, clock);
        int validDuration = Validators.duration(newDurationMinutes);
        conflictService.ensureAvailable(repository.allAppointments(), validStart, validDuration, id);
        appointment.reschedule(validStart, validDuration);
        repository.saveAppointment(appointment);
        persistence.save();
        return appointment;
    }

    public Appointment cancel(UUID id) {
        Appointment appointment = requireScheduled(id);
        appointment.cancel();
        repository.saveAppointment(appointment);
        persistence.save();
        return appointment;
    }

    public Appointment findById(UUID id) {
        return repository.findAppointment(id)
                .orElseThrow(() -> new NotFoundException("Appointment not found: " + id));
    }

    public List<Appointment> list(AppointmentFilter filter) {
        AppointmentFilter safeFilter = filter == null ? AppointmentFilter.all() : filter;
        Predicate<Appointment> matches = appointment ->
                (safeFilter.date() == null || appointment.getStartTime().toLocalDate().equals(safeFilter.date()))
                        && (safeFilter.status() == null || appointment.getStatus() == safeFilter.status())
                        && (safeFilter.type() == null || appointment.getType() == safeFilter.type())
                        && (safeFilter.priority() == null || appointment.getPriority() == safeFilter.priority());

        return repository.allAppointments().stream()
                .filter(matches)
                .sorted(CHRONOLOGICAL_ORDER)
                .toList();
    }

    public List<Appointment> triageQueue(LocalDate date) {
        if (date == null) {
            throw new ValidationException("A date is required for the triage queue.");
        }
        PriorityQueue<Appointment> queue = new PriorityQueue<>(TRIAGE_ORDER);
        list(new AppointmentFilter(date, AppointmentStatus.SCHEDULED, null, null)).forEach(queue::offer);

        java.util.ArrayList<Appointment> ordered = new java.util.ArrayList<>();
        while (!queue.isEmpty()) {
            ordered.add(queue.poll());
        }
        return List.copyOf(ordered);
    }

    private Appointment requireScheduled(UUID id) {
        Appointment appointment = findById(id);
        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new ValidationException("A cancelled appointment cannot be changed.");
        }
        return appointment;
    }

    private void requireAnimal(UUID animalId) {
        if (animalId == null || repository.findAnimal(animalId).isEmpty()) {
            throw new NotFoundException("An existing animal is required for the appointment.");
        }
    }

    private AppointmentType requireType(AppointmentType type) {
        if (type == null) {
            throw new ValidationException("Appointment type is required.");
        }
        return type;
    }

    private PatientCondition requireCondition(PatientCondition condition) {
        if (condition == null) {
            throw new ValidationException("Patient condition is required.");
        }
        return condition;
    }

    private Set<ClinicalSign> copySigns(Set<ClinicalSign> signs) {
        if (signs == null || signs.isEmpty()) {
            return Set.of();
        }
        if (signs.contains(null)) {
            throw new ValidationException("Clinical signs cannot contain an empty value.");
        }
        return Set.copyOf(new HashSet<>(signs));
    }
}

