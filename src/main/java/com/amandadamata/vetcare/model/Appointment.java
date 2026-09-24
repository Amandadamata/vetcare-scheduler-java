package com.amandadamata.vetcare.model;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Appointment {
    private UUID id;
    private UUID animalId;
    private AppointmentType type;
    private LocalDateTime startTime;
    private int durationMinutes;
    private PatientCondition condition;
    private Set<ClinicalSign> clinicalSigns;
    private String notes;
    private TriagePriority priority;
    private AppointmentStatus status;
    private Instant createdAt;

    @SuppressWarnings("unused")
    private Appointment() {
        // Required by Gson.
    }

    public Appointment(
            UUID id,
            UUID animalId,
            AppointmentType type,
            LocalDateTime startTime,
            int durationMinutes,
            PatientCondition condition,
            Set<ClinicalSign> clinicalSigns,
            String notes,
            TriagePriority priority,
            AppointmentStatus status,
            Instant createdAt) {
        this.id = id;
        this.animalId = animalId;
        this.type = type;
        this.startTime = startTime;
        this.durationMinutes = durationMinutes;
        this.condition = condition;
        this.clinicalSigns = new HashSet<>(clinicalSigns);
        this.notes = notes;
        this.priority = priority;
        this.status = status;
        this.createdAt = createdAt;
    }

    public LocalDateTime getEndTime() {
        return startTime.plusMinutes(durationMinutes);
    }

    public void reschedule(LocalDateTime startTime, int durationMinutes) {
        this.startTime = startTime;
        this.durationMinutes = durationMinutes;
    }

    public void updateClinicalDetails(
            AppointmentType type,
            PatientCondition condition,
            Set<ClinicalSign> clinicalSigns,
            String notes,
            TriagePriority priority) {
        this.type = type;
        this.condition = condition;
        this.clinicalSigns = new HashSet<>(clinicalSigns);
        this.notes = notes;
        this.priority = priority;
    }

    public void cancel() {
        this.status = AppointmentStatus.CANCELLED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAnimalId() {
        return animalId;
    }

    public AppointmentType getType() {
        return type;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public PatientCondition getCondition() {
        return condition;
    }

    public Set<ClinicalSign> getClinicalSigns() {
        return Set.copyOf(clinicalSigns);
    }

    public String getNotes() {
        return notes;
    }

    public TriagePriority getPriority() {
        return priority;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

